(ns clj-parasoup.database.memory.service
  (:require [clojure.tools.logging :as log]
            [clojure.core.async :as as]
            [clj-parasoup.database.memory.core :as core]
            [clj-parasoup.database.protocol :as dbp]
            [puppetlabs.trapperkeeper.core :as trapperkeeper]
            [puppetlabs.trapperkeeper.app :refer [get-service]]))

(trapperkeeper/defservice
  memory-database-service
  dbp/DatabaseService
  [[:ConfigService get-in-config]]
  (start [this context]
         (log/info "Starting memory database")
         (assoc context :cache (atom {})))
  (put-file [this file-name byte-data content-type]
            (core/put-file (:cache (service-context this)) file-name byte-data content-type))
  (get-file [this file-name] (core/get-file (:cache (service-context this)) file-name)))
