(ns clj-parasoup.http-proxy.service
  (:require [clj-parasoup.http-proxy.core :as proxy-core]
            [puppetlabs.trapperkeeper.core :as trapperkeeper]))

(defprotocol HttpProxyService
  (proxy-request [this request domain]))

(trapperkeeper/defservice http-proxy-service
                          HttpProxyService
                          [[:ConfigService get-in-config]]
                          (proxy-request [this request domain] (proxy-core/relay request domain)))
