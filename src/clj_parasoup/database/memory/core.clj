(ns clj-parasoup.database.memory.core
  (:require [clojure.core.async :as as]
            [clojure.tools.logging :as log]))

(defn put-file [cache file-name byte-data content-type]
  (when (not (and (nil? file-name) (nil? content-type)))
    (log/info "put file " file-name " " content-type)
    (swap! cache assoc file-name {:content-type content-type
                                  :byte-data byte-data})))

(defn get-file [cache file-name]
  (log/info "get file " file-name)
  (if-let [data (get @cache file-name)]
    (as/to-chan [data])
    (as/to-chan [])))

(defn create-cache [] (atom {}))
