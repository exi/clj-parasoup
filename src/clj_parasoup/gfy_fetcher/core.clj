(ns clj-parasoup.gfy-fetcher.core
  (:import [org.jboss.netty.buffer ChannelBuffer])
  (:require [clojure.core.async :as as]
            [clojure.string :as string]
            [org.httpkit.client :as http]
            [clj-parasoup.util.util :as util]
            [clj-parasoup.database.protocol :as dbp]
            [clojure.tools.logging :as log]
            [clojure.data.json :as json]))

(defn enqueue [chan url]
  (when (not (re-matches #".*-square.gif" url))
    (as/go  (as/>! chan url))))

(defn on-gfy-response [db uri response]
  (when-let [body (:body response)]
    (when-let [data (json/read-str body :key-fn keyword)]
      (let [gfy-name (:gfyname data)]
        (log/info "put gfy" uri gfy-name)
        (dbp/put-gfy db uri gfy-name)))))

(defn fetch [db url]
  (let [uri (util/extract-gif-uri-from-url url)]
    (http/request {:url (str "http://upload.gfycat.com/transcode?fetchUrl=" url)
                   :method :get
                   :timeout 25000}
                  #(on-gfy-response db uri %))))

(defn skip? [db url]
  (let [uri (util/extract-gif-uri-from-url url)]
    (not (nil? (as/<!! (dbp/get-gfy db uri))))))

(defn fetcher [chan db stop]
  (log/info "fetcher start")
  (as/go
   (loop [url (as/<! chan)]
     (when (and url (not @stop))
       (fetch db url)
       (when (not (skip? db url)) (as/<! (as/timeout 30000)))
       (recur (as/<! chan))))))
