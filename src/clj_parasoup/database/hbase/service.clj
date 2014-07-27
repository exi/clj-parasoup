(ns clj-parasoup.database.hbase.service
  (:require [clojure.tools.logging :as log]
            [clojure.core.async :as as]
            [clj-hbase.core :as hb]
            [clj-parasoup.database.hbase.core :as core]
            [clj-parasoup.database.protocol :as dbp]
            [puppetlabs.trapperkeeper.core :as trapperkeeper]
            [puppetlabs.trapperkeeper.app :refer [get-service]]))

(trapperkeeper/defservice
  hbase-database-service
  dbp/DatabaseService
  [[:ConfigService get-in-config]]
  (start [this context]
         (log/info "Starting hbase database")
         (let [resource (get-in-config [:hbase :hbase-site-xml])
               db (hb/create-database {:resources [resource]})]
           (log/info "using resource" resource)
           (core/ensure-files-table db (get-in-config [:hbase :files-table]))
           (assoc context :db db)))
  (put-file [this file-name byte-data content-type]
            (core/put-file (:db (service-context this))
                           (get-in-config [:hbase :files-table])
                           file-name
                           byte-data
                           content-type))
  (get-file [this file-name]
            (core/get-file (:db (service-context this))
                           (get-in-config [:hbase :files-table])
                           file-name)))
