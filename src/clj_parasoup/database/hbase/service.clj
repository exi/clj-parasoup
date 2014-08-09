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
           (core/ensure-tokens-table db (get-in-config [:hbase :tokens-table]))
           (assoc context :db db)))
  (stop [this context]
        (log/info "Stopping hbase database")
        (hb/disconnect (:db context))
        (dissoc context :db))
  (put-file [this file-name byte-data content-type]
            (core/put-file (:db (service-context this))
                           (get-in-config [:hbase :files-table])
                           file-name
                           byte-data
                           content-type))
  (get-file [this file-name]
            (core/get-file (:db (service-context this))
                           (get-in-config [:hbase :files-table])
                           file-name))
  (get-gfy [this file-name]
           (core/get-gfy (:db (service-context this))
                         (get-in-config [:hbase :files-table])
                         file-name))
  (put-gfy [this file-name gfy-name]
           (core/put-gfy (:db (service-context this))
                         (get-in-config [:hbase :files-table])
                         file-name
                         gfy-name))
  (check-file [this file-name]
              (core/check-file (:db (service-context this))
                             (get-in-config [:hbase :files-table])
                             file-name))
  (put-token [this token data]
             (core/put-token (:db (service-context this))
                             (get-in-config [:hbase :tokens-table])
                             token
                             data))
  (get-token [this token]
             (core/get-token (:db (service-context this))
                             (get-in-config [:hbase :tokens-table])
                             token)))
