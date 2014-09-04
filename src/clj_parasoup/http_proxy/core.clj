(ns clj-parasoup.http-proxy.core
  (:import [org.jboss.netty.buffer ChannelBuffer]
           [org.apache.commons.io IOUtils])
  (:require [clojure.core.async :as as]
            [clojure.string :as string]
            [org.httpkit.client :as http]
            [clojure.tools.logging :as log]
            [clj-parasoup.util.map :as umap]))

(defn text-domain->soup [domain text]
  (string/replace text domain "soup.io"))

(defn text-soup->domain [domain text]
  (string/replace text "soup.io" domain))

(defn text-https->http [text] (string/replace text "https://" "http://"))
(defn text-http->https[text] (string/replace text "http://" "https://"))
(defn unwrap-bytes [body]
  (cond
    (instance? java.io.InputStream body) (IOUtils/toByteArray body)
    :else body))

(defn header-domain->soup
  ([domain header] (header-domain->soup domain header false))
  ([domain header usehttps]
   (let [newheader (umap/map-over-map-values header (partial text-domain->soup domain))]
     (if usehttps
       (umap/map-over-map-values newheader text-http->https)
       newheader))))

(defn header-soup->domain [domain header]
  (umap/map-over-map-values header (partial text-soup->domain domain)))

(def method->client-map {:get http/get
                         :post http/post
                         :head http/head})

(defn request->scheme [request]
  (cond
    (= "/login" (:uri request)) "https://"
    :else "http://"))

(defn wrap-request [opts]
  (let [output (as/chan)]
    (http/request (reduce (fn [acc [k v]] (if (nil? v) (dissoc acc k) acc))
                          opts
                          opts)
                  (fn [resp] (as/go (as/>! output resp))))
    output))

(defn postprocess-soup-headers [response domain]
  (-> (:headers response)
      (umap/map-over-map-values (fn [v] (if (coll? v)
                                          (map (partial string/lower-case) v)
                                          (string/lower-case v))))
      (umap/map-over-map-values (fn [v] (let [transf (fn [t] (->> t
                                                                  (text-soup->domain domain)
                                                                  (text-https->http)))]
                                          (if (coll? v)
                                            (map transf v)
                                            (transf v)))))
      (umap/map-over-map-keys (fn [k] (string/lower-case (if (keyword? k) (name k) k))))
      (dissoc "server" "status" "connection" "content-encoding" "date" "content-length")))

(defn postprocess-soup-response [response domain]
  (assoc response :headers (postprocess-soup-headers response domain)
                  :body (if (string? (:body response))
                          (->> (:body response)
                               (text-soup->domain domain)
                               (text-https->http))
                          (->> (:body response)
                               (unwrap-bytes)))))

(defn format-url [request headers scheme]
  (format "%s%s%s%s"
          scheme
          (get headers "host")
          (:uri request)
          (if (:query-string request)
            (str "?" (:query-string request))
            "")))

(defn generate-request-headers [headers domain scheme]
  (dissoc
   (header-domain->soup domain headers (= "https://" scheme))
   "host" "content-length" "accept-encoding"))

(defn wrap-body [body]
  (if (instance? ChannelBuffer body)
    (.toByteBuffer body)
    body))

(defn relay [request domain]
  (let [scheme (request->scheme request)
        headers (umap/map-over-map-keys (:headers request) string/lower-case)
        newtarget (text-domain->soup domain (format-url request headers scheme))
        opts {:url newtarget
              :method (:request-method request)
              :headers (generate-request-headers headers domain scheme)
              :as (when (re-find #"(?i)(\.gif|\.png|\.jpg|\.jpeg)$" (:uri request))
                    :byte-array)
              :body (wrap-body (:body request))
              :follow-redirects false
              :timeout 60000}
        response-channel (as/chan)]
    (as/go
      (let [response (as/<! (wrap-request opts))
            processed-response (postprocess-soup-response response domain)]
        (as/>! response-channel (reduce
                                  #(assoc %1 %2 (get processed-response %2))
                                  {}
                                  [:status :headers :body]))))
    response-channel))
