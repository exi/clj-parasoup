(ns clj-parasoup.request-manager.core
  (:import (org.jboss.netty.buffer ChannelBuffer))
  (:require [clojure.core.async :as as]
            [clojure.string :as string]
            [org.httpkit.client :as http]
            [clojure.tools.logging :as log]))


(defn request-dispatcher [response-channel request domain proxy-fn]
  (log/info (:uri request))
  (as/go
    (as/pipe (proxy-fn request domain) response-channel)))

(defn create-request-dispatcher [domain proxy-fn]
  (fn [response-channel request]
    (request-dispatcher response-channel request domain proxy-fn)))

