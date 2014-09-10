(ns clj-parasoup.request-manager.core
  (:require [clojure.core.async :as as]
            [clojure.string :as string]
            [org.httpkit.client :as http]
            [clj-parasoup.database.protocol :as dbp]
            [clj-parasoup.http-auth.service :as auth]
            [clj-parasoup.util.util :as util]
            [clojure.tools.logging :as log]
            [digest]))

(def gfycat-embed-code "<script type=\"text/javascript\">
  var lastGfy = new Date().getTime();
  function removeGfyItems() {
    var items = document.querySelectorAll('.gfyitem');
    for (var i = 0; i < items.length ; i++) {
      items[i].removeAttribute('class');
    }
  }
  function updateGfy() {
    if (new Date().getTime() - lastGfy > 1000) {
      gfyCollection.init();
      lastGfy = new Date().getTime();
      removeGfyItems();
      console.log('update');
    }
  }
  function loadGfy() {
    if (typeof gfyCollection !== 'undefined') return;
    console.log(\"gfy!\");
    (function(d, t){
      var g = d.createElement(t);
      var s = d.getElementsByTagName(t)[0];
      if (typeof s === 'undefined' || typeof s.parentNode === 'undefined') {
        return;
      }

      g.src = 'http://assets.gfycat.com/js/gfyajax-0.517d.js';
      s.parentNode.insertBefore(g, s);}(document, 'script')
    );
    document.body.addEventListener('DOMSubtreeModified', updateGfy, false);
  }
  </script>")

(def gfycat-run-code "<script type=\"text/javascript\">
  if (loadGfy) {
    loadGfy();
  }
  </script>")

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

(defn add-gfycat-to-head [body]
  (-> body
      (string/replace #"</head>" (str gfycat-embed-code "</head>") )
      (string/replace  #"</body>" (str gfycat-run-code "</body>"))))

(defn fetch-gfys [gifs opts]
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

(defn replace-found-gfys [body gfy-list]
  (reduce
   (fn [body gfy]
     (let [new  (string/replace
                 body
                 (str "src=\"" (:gif gfy) "\"")
                 (str "class=\"gfyitem\" data-id=\"" (:gfy gfy) "\""))]
       new))
   body
   gfy-list))

(defn gif->gfycat [response opts]
  (log/debug "gfycat" (get-in opts [:request :uri]))
  (if (or (nil? (get-in response [:headers "content-type"]))
          (not (re-matches #".*text/html.*" (get-in response [:headers "content-type"])))
          (not (string? (:body response)))
          (not (= 200 (:status response)))
          (re-matches #"/remote/.*" (get-in opts [:request :uri])))
    response
    (let [body (:body response)
          gfys (fetch-gfys (re-seq #"http://asset-[^\"]+\.gif" body) opts)]
      (assoc
        response
        :body
        (-> body
            (add-gfycat-to-head)
            (replace-found-gfys gfys))))))

(defn apply-etag [response request]
  (log/debug "etag")
  (if (= 200 (:status response))
    (assoc-in response [:headers "etag"] (create-etag request))
    response))

(defn responde-from-soup [opts]
  (log/debug "from soup" (get-in opts [:request :uri]))
  (as/go
    (let [response-channel (:response-channel opts)
          request (:request opts)
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
      (log/debug "soup status" (:status response) (get-in opts [:request :uri]))
      (if (nil? response)
        (as/>! response-channel {:body "Soup.io seems to have a problem" :status 500})
        (as/>!
          response-channel
          (-> response
              (gif->gfycat opts)
              (apply-etag request)))))))

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
