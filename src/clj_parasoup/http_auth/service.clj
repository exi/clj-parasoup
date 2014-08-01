(ns clj-parasoup.http-auth.service
  (:require [clj-parasoup.http-auth.core :as core]
            [puppetlabs.trapperkeeper.core :as trapperkeeper]
            [puppetlabs.trapperkeeper.app :refer [get-service]]))


(defprotocol HttpAuthService
  (authenticated? [this request])
  (send-auth-request [this response-channel]))

(trapperkeeper/defservice
  http-auth-service
  HttpAuthService
  [[:ConfigService get-in-config]
   DatabaseService]
  (authenticated? [this request]
                  (core/authenticated?
                   (get-service this :DatabaseService)
                   (get-in-config [:http-auth :username])
                   (get-in-config [:http-auth :password])
                   request))
  (send-auth-request [this response-channel]
                  (core/send-auth-request
                   (get-service this :DatabaseService)
                   response-channel)))
