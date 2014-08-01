(ns clj-parasoup.request-manager.core
  (:require [clojure.core.async :as as]
            [clojure.string :as string]
            [org.httpkit.client :as http]
            [clj-parasoup.database.protocol :as dbp]
            [clj-parasoup.http-auth.service :as auth]
            [clojure.tools.logging :as log]))

(defn asset-request? [request]
  (not (nil? (re-find #"^asset-" (get-in request [:headers "host"])))))

(defn responde-with-data [response-channel file-data]
  (as/go (as/>! response-channel {:status 200
                                  :body (:byte-data file-data)
                                  :headers {"content-type" (:content-type file-data)}})))

(defn handle-authenticated-request
  [opts]
  (let [request (:request opts)
        response-channel (:response-channel opts)]
    (as/go
     (if-let [file-data (when (asset-request? request)
                          (as/<! (dbp/get-file (:db opts) (:uri request))))]
       (do
         (responde-with-data response-channel file-data))
       (let [response (as/<! ((:proxy-fn opts)
                              request
                              (:domain opts)))]
         (when (and (= 200 (:status response)) (asset-request? request))
           (dbp/put-file (:db opts)
                         (:uri request)
                         (:body response)
                         (get-in response [:headers "content-type"])))
         (as/>! response-channel response))))))

(defn request-dispatcher
  [opts]
  (let [request (:request opts)
        auth-service (:auth opts)]
    (log/info (get-in request [:headers "host"]) (:uri request))
    (when (= "/shutdown" (:uri request)) ((:shutdown opts)))
    (if (not (auth/authenticated? auth-service request))
      (auth/send-auth-request auth-service (:response-channel opts))
      (handle-authenticated-request))))

(defn create-request-dispatcher [opts]
  (fn [response-channel request]
    (request-dispatcher (assoc opts
                          :response-channel response-channel
                          :request request))))
