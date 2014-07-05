(ns clj-parasoup.http.service
  (:require [clojure.tools.logging :as log]
            [clj-parasoup.http.core :as core]
            [puppetlabs.trapperkeeper.core :as trapperkeeper]
            ))


(defprotocol HttpService
  (set-request-handler [this newhandler]))

(trapperkeeper/defservice aleph-http-service
                          HttpService
                          [[:ConfigService get-in-config]]
                          (start [this context]
                                 (log/info "Starting httpservice")
                                 (let [handler (core/default-handler)
                                       server-stop (core/start-server (get-in-config [:aleph-http]) handler)]
                                   (assoc context :server-stop server-stop :handler handler)))
                          (stop [this context]
                                (log/info "Stopping httpservice")
                                ((:server-stop context))
                                context)
                          (set-request-handler [this new-handler](reset! (:handler (service-context this)) new-handler)))
