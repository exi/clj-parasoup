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
   DatabaseService
   [:GfyFetcher fetch]]
  (start [this context]
         (log/info "Starting requestmanagerservice")
         (set-request-handler (core/create-request-dispatcher
                               {:domain (let [domain (get-in-config [:parasoup :domain])
                                              port (Integer. (get-in-config [:parasoup :port]))]
                                          (if (= 80 port)
                                            domain
                                            (str domain ":" port)))
                                :db (get-service this :DatabaseService)
                                :auth (get-service this :HttpAuthService)
                                :gfy-fetcher fetch
                                :proxy-fn proxy-request
                                :shutdown request-shutdown}))
         context))
