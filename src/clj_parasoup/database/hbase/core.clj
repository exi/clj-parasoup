(ns clj-parasoup.database.hbase.core
  (:require [clojure.core.async :as as]
            [clj-hbase.core :as hb]
            [clojure.tools.logging :as log]
            [taoensso.nippy :as nippy]))

(defn get-first-column-value [result family]
  (let [columns (get result family)
        column (get columns (first (keys columns)))
        data (get column (first (keys column)))]
    data))

(defn put-file [db files-table file-name byte-data content-type]
  (when (not (and
              (nil? file-name)
              (nil? content-type)
              (not (instance? (type (byte-array 0)) byte-data))))
    (hb/put db files-table file-name {:byte-data byte-data
                                      :content-type content-type})))

(defn fetch-file [db files-table file-name]
  (let [result (hb/get
                 db
                 files-table
                 file-name
                 {}
                 {:family #(keyword (String. %))
                 :value {:content-type {:* #(String. %)}}})]
    (when-not (empty? result)
      (let [data {:byte-data (get-first-column-value result :byte-data)
                  :content-type (get-first-column-value result :content-type)}]
        (when (not-any? nil? (vals data))
          data)))))

(defn fetch-gfy [db files-table file-name]
  (let [result (hb/get
                db
                files-table
                file-name
                {:family :gfy-name}
                {:family #(keyword (String. %))
                 :value {:gfy-name {:* #(String. %)}}})]
    (when-not (empty? result)
      (get-first-column-value result :gfy-name))))

(defn get-file [db files-table file-name]
  (if-let [data (fetch-file db files-table file-name)]
    (as/to-chan [data])
    (as/to-chan [])))

(defn get-gfy [db files-table file-name]
  (if-let [data (fetch-gfy db files-table file-name)]
    (as/to-chan [data])
    (as/to-chan [])))

(defn put-gfy [db files-table file-name gfy-name]
  (when (not (or (nil? file-name) (nil? gfy-name)))
    (hb/put db files-table file-name {:gfy-name gfy-name})))

(defn check-file [db files-table file-name]
  (as/to-chan [(hb/exists db files-table file-name {})]))

(defn put-token [db token-table token data]
  (log/info "put token" token data)
  (when (not (nil? token))
    (hb/put db token-table token {:data (nippy/freeze data)})))

(defn get-token [db token-table token]
  (let [result (hb/get
                db
                token-table
                token
                {}
                {:family #(keyword (String. %))})
        data (get-first-column-value result :data)]
    (when data (nippy/thaw data))))

(defn ensure-files-table [db table-name]
  (when (not (hb/table-exists? db table-name))
    (hb/create-table db table-name [:byte-data :content-type :gfy-name])))

(defn ensure-tokens-table [db table-name]
  (when (not (hb/table-exists? db table-name))
    (hb/create-table db table-name [:data])))
