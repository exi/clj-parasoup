(ns clj-parasoup.request-manager.core
  (:require [clojure.core.async :as as]
            [clojure.string :as string]
            [org.httpkit.client :as http]
            [clj-parasoup.database.protocol :as dbp]
            [clj-parasoup.http-auth.service :as auth]
            [clj-parasoup.util.util :as util]
            [clojure.tools.logging :as log]
            [digest]))

(def gfycat-embed-code "<script>(function(d, t){var g = d.createElement(t),s = d.getElementsByTagName(t)[0];g.src = 'http://assets.gfycat.com/js/gfyajax-0.517d.js';s.parentNode.insertBefore(g, s);}(document, 'script'));</script>")

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

(defn add-gfycat-headers [body]
  (string/replace body #"</body>" (str gfycat-embed-code "</body>")))

(defn fetch-gfys [gifs opts]
  (log/info "fetch gfys for" gifs)
   (into
    []
    (->>
     (map
      (fn [gif]
        (let [fetched {:gif gif :gfy (as/<!! (dbp/get-gfy (:db opts) (util/extract-gif-uri-from-url gif)))}]
          (when (nil? (:gfy fetched))
            ((:gfy-fetcher opts) gif))
          fetched))
      gifs)
     (remove #(nil? (:gfy %1))))))

(defn replace-found-gfys [body gfy-map]
  (log/info "replace gfys" gfy-map)
  (reduce
   (fn [body gfy]
     (let [new  (string/replace
                 body
                 (str "src=\"" (:gif gfy) "\"")
                 (str "class=\"gfyitem\" data-id=\"" (:gfy gfy) "\""))]
       (log/info gfy)
       new))
   body
   gfy-map))

(defn gif->gfycat [response opts]
    (if (not (re-matches #".*text/html.*" (get-in response [:headers "content-type"])))
      response
      (let [body (:body response)
            gfys (fetch-gfys (re-seq #"http://asset-[^\"]+\.gif" body) opts)]
        (assoc
          response
          :body
          (-> body
              (add-gfycat-headers)
              (replace-found-gfys gfys))))))

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
      (assoc-in (gif->gfycat response opts)
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
