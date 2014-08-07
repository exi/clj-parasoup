(ns clj-parasoup.request-manager.core
  (:require [clojure.core.async :as as]
            [clojure.string :as string]
            [org.httpkit.client :as http]
            [clj-parasoup.database.protocol :as dbp]
            [clj-parasoup.http-auth.service :as auth]
            [clojure.tools.logging :as log]))

(def max-asset-cache-lifetime-in-seconds (* 60 60 24 365))

(defn asset-request? [request]
  (not (nil? (re-find #"^asset-" (get-in request [:headers "host"])))))

(defn responde-with-data [response-channel file-data]
  (as/go (as/>! response-channel
                {:status 200
                 :body (:byte-data file-data)
                 :headers {"content-type" (:content-type file-data)
                           "cache-control" (str "public, max-age=" max-asset-cache-lifetime-in-seconds)}})))
(defn log-access [request hit]
  (log/info
   (if hit "hit" "miss")
   (str (get-in request [:headers "host"]) (:uri request))))

(defn handle-authenticated-request
  [opts]
  (let [request (:request opts)
        response-channel (:response-channel opts)]
    (as/go
     (if-let [file-data (when (asset-request? request)
                          (as/<! (dbp/get-file (:db opts) (:uri request))))]
       (do
         (log-access request true)
         (responde-with-data response-channel file-data))
       (let [response (as/<! ((:proxy-fn opts)
                              request
                              (:domain opts)))]
         (when (and (= 200 (:status response))
                    (asset-request? request))
           (log-access request false)
           (dbp/put-file (:db opts)
                         (:uri request)
                         (:body response)
                         (get-in response [:headers "content-type"])))
         (as/>! response-channel response))))))

(defn request-dispatcher
  [opts]
  (let [request (:request opts)
        auth-service (:auth opts)]
    (when (= "/shutdown" (:uri request)) ((:shutdown opts)))
    (if (asset-request? request)
      (handle-authenticated-request opts)
      (auth/handle-request auth-service opts handle-authenticated-request))))

(defn create-request-dispatcher [opts]
  (fn [response-channel request]
    (request-dispatcher (assoc opts
                          :response-channel response-channel
                          :request request))))
