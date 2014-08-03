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
   [:ShutdownService request-shutdown]
   HttpAuthService
   DatabaseService]
  (start [this context]
         (log/info "Starting requestmanagerservice")
         (set-request-handler (core/create-request-dispatcher
                               {:domain (str
                                         (get-in-config [:parasoup :domain])
                                         ":"
                                         (get-in-config [:parasoup :port]))
                                :db (get-service this :DatabaseService)
                                :auth (get-service this :HttpAuthService)
                                :proxy-fn proxy-request
                                :shutdown request-shutdown}))
         context))
