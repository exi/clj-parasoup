(ns clj-parasoup.request-manager.core
  (:require [clojure.core.async :as as]
            [clojure.string :as string]
            [org.httpkit.client :as http]
            [clj-parasoup.database.protocol :as dbp]
            [clj-parasoup.http-auth.service :as auth]
            [clojure.tools.logging :as log]
            [digest]))

(def max-asset-cache-lifetime-in-seconds (* 60 60 24 365))

(defn asset-request? [request]
  (not (nil? (re-find #"^asset-" (get-in request [:headers "host"])))))

(defn create-etag [request]
  (digest/md5 (:uri request)))

(defn log-access [request hit]
  (log/info
   (if hit "hit" "miss")
   (str (get-in request [:headers "host"]) (:uri request))))


(def cache-control-header (str "public, max-age=" max-asset-cache-lifetime-in-seconds))

(defn responde-from-cache [opts file-data]
  (log-access (:request opts) true)
  (as/go (as/>! (:response-channel opts)
                {:status 200
                 :body (:byte-data file-data)
                 :headers {"content-type" (:content-type file-data)
                           "cache-control" cache-control-header
                           "etag" (create-etag (:request opts))}})))

(defn responde-from-soup [opts]
  (as/go
   (let [request (:request opts)
         response (as/<! ((:proxy-fn opts)
                          request
                          (:domain opts)))]
     (when (and (= 200 (:status response))
                (asset-request? request))
       (log-access request false)
       (dbp/put-file (:db opts)
                     (:uri request)
                     (:body response)
                     (get-in response [:headers "content-type"])))
     (as/>!
      (:response-channel opts)
      (assoc-in response
                [:headers "etag"]
                (create-etag (:request opts)))))))

(defn responde-with-304 [opts]
  (as/go (as/>!
          (:response-channel opts)
          {:status 304
           :body nil
           :headers {"etag" (create-etag (:request opts))
                     "cache-control" cache-control-header}})))

(defn handle-authenticated-request
  [opts]
  (let [request (:request opts)
        etag (get-in request [:headers "if-none-match"])]
    (as/go
     (if (and (asset-request? request)
              etag
              (dbp/check-file (:db opts) (:uri request)))
       (responde-with-304 opts)
       (if-let [file-data (when (asset-request? request)
                            (as/<! (dbp/get-file (:db opts) (:uri request))))]
         (responde-from-cache opts file-data)
         (responde-from-soup opts))))))

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
