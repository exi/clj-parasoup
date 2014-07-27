(ns clj-parasoup.database.hbase.core
  (:require [clojure.core.async :as as]
            [clj-hbase.core :as hb]
            [clojure.tools.logging :as log]))

(defn get-first-column-value [result family]
  (let [columns (get result family)
        column (get columns (first (keys columns)))
        data (get column (first (keys column)))]
    data))

(defn put-file [db files-table file-name byte-data content-type]
  (when (not (and (nil? file-name) (nil? content-type)))
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
    (if (empty? result)
      nil
      {:byte-data (get-first-column-value result :byte-data)
       :content-type (get-first-column-value result :content-type)})))

(defn get-file [db files-table file-name]
  (if-let [data (fetch-file db files-table file-name)]
    (as/to-chan [data])
    (as/to-chan [])))


(defn ensure-files-table [db table-name]
  (when (not (hb/table-exists? db table-name))
    (hb/create-table db table-name [:byte-data :content-type])))
