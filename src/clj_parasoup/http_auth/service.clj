(ns clj-parasoup.http-auth.service
  (:require [clj-parasoup.http-auth.core :as core]
            [puppetlabs.trapperkeeper.core :as trapperkeeper]
            [puppetlabs.trapperkeeper.app :refer [get-service]]))


(defprotocol HttpAuthService
  (handle-request [this opts next-fn]))

(trapperkeeper/defservice
  http-auth-service
  HttpAuthService
  [[:ConfigService get-in-config]
   DatabaseService]
  (handle-request [this opts next-fn]
                  (core/handle-auth-request
                   (get-service this :DatabaseService)
                   (get-in-config [:http-auth :username])
                   (get-in-config [:http-auth :password])
                   (get-in-config [:parasoup :domain])
                   opts next-fn)))
