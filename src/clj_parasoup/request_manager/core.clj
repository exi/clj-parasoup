(ns clj-parasoup.request-manager.core
  (:require [clojure.core.async :as as]
            [clojure.string :as string]
            [org.httpkit.client :as http]
            [clj-parasoup.database.protocol :as dbp]))

(defn asset-request? [request]
  (not (nil? (re-find #"^asset-" (get-in request [:headers "host"])))))

(defn responde-with-data [response-channel file-data]
  (as/go (as/>! response-channel {:status 200
                                  :body (:byte-data file-data)
                                  :headers {"content-type" (:content-type file-data)}})))

(defn request-dispatcher [response-channel request domain proxy-fn db]
  (as/go
   (if-let [file-data (when (asset-request? request)
                        (as/<! (dbp/get-file db (:uri request))))]
     (do
       (responde-with-data response-channel file-data))
     (let [response (as/<! (proxy-fn request domain))]
       (when (asset-request? request)
         (dbp/put-file db
                       (:uri request)
                       (:body response)
                       (get-in response [:headers "content-type"])))
       (as/>! response-channel response)))))

(defn create-request-dispatcher [domain proxy-fn db]
  (fn [response-channel request]
    (request-dispatcher response-channel request domain proxy-fn db)))
