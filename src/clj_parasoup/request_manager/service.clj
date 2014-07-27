(ns clj-parasoup.request-manager.service
  (:require [clojure.tools.logging :as log]
            [clj-parasoup.request-manager.core :as core]
            [puppetlabs.trapperkeeper.core :as trapperkeeper]
            [puppetlabs.trapperkeeper.app :refer [get-service]]))


(defprotocol RequestManagerService)

(trapperkeeper/defservice
  request-manager-service
  RequestManagerService
  [[:ConfigService get-in-config]
   [:HttpService set-request-handler]
   [:HttpProxyService proxy-request]
   DatabaseService]
  (start [this context]
         (log/info "Starting requestmanagerservice")
         (set-request-handler (core/create-request-dispatcher
                               (get-in-config [:parasoup :real-domain])
                               proxy-request
                               (get-service this :DatabaseService)))
         context))
