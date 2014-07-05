(ns clj-parasoup.http.core
  (:require [clojure.core.async :as as]
            [clj-parasoup.util.async :as uas]
            [aleph.http :as ahttp]
            [aleph.formats :as af]
            [lamina.core :as lc]
            [clojure.tools.logging :as log]))


(defn receive-complete-lamina-channel [channel]
  (let [ret (as/chan)
        agg (lc/reduce* conj [] channel)]
    (lc/on-closed
     channel
     (fn []
       (as/go
        (as/>! ret (af/channel-buffers->channel-buffer @agg)))))
    ret))

(defn unwrap-async-body [body]
  (let [r-chan (as/chan)]
    (as/go
     (let [result (if (and body (lc/channel? body))
                    (as/<! (receive-complete-lamina-channel body))
                    body)]
       (as/>! r-chan result)))
    r-chan))

(defn async-body? [data]
  (let [body (:body data)]
    (lc/channel? body)))

(defn handle-request [handler aleph-channel request]
  (let [chan (as/chan)]
    (as/go
     (let [response (as/<! chan)]
       (lc/enqueue aleph-channel response)
       (as/close! chan)))
    (as/go
     (@handler chan (if (async-body? request)
                      (assoc request :body (as/<! (unwrap-async-body (:body request))))
                      request)))))

(defn aleph-handler [handler]
  (fn [aleph-channel request] (handle-request handler aleph-channel request)))

(defn default-handler []
  (atom (fn [channel request]
          (as/go
            (let [timeout (as/timeout 100)
                  [c v] (as/alts! [timeout])]
              (as/>! channel {:status 404
                              :headers {"content-type" "text/plain"}
                              :body "Not Found"})
              (as/close! channel))))))

(defn start-server [config handler]
  (ahttp/start-http-server (aleph-handler handler) config))
