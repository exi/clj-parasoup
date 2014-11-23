(ns clj-parasoup.gfy-fetcher.service
  (:require [clojure.tools.logging :as log]
            [clojure.core.async :as as]
            [clj-parasoup.gfy-fetcher.core :as core]
            [puppetlabs.trapperkeeper.core :as trapperkeeper]
            [puppetlabs.trapperkeeper.app :refer [get-service]]))


(defprotocol GfyFetcher
  (fetch [this url]))

(trapperkeeper/defservice
  gfy-fetcher-service
  GfyFetcher
  [[:ConfigService get-in-config]
   [:ShutdownService request-shutdown]
   DatabaseService]
  (start [this context]
         (log/info "Starting gfyfetcher")
         (let [chan (as/chan (as/sliding-buffer 1000))
               stop (atom false)]
           (if (not (get #{"true" "yes" "1"} (get-in-config [:gfy-fetcher :disable])))
             (core/fetcher
               chan
               (get-service this :DatabaseService)
               stop)
             (log/info "gfyfetcher disabled by configuration")
             )
           (assoc context :stop stop :chan chan)))
  (stop [this context]
        (reset! (:stop context) false)
        (as/close! (:chan context))
        (dissoc context :chan :stop))
  (fetch [this url]
         (core/enqueue (:chan (service-context this)) url)))
