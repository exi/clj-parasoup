(ns clj-parasoup.http-auth.core
  (:require [clj-parasoup.database.protocol :as dbp]
            [clojure.data.codec.base64 :as b64]
            [clojure.tools.logging :as log]
            [clojure.core.async :as as]
            [clojure.string :as string]))

(def cookie-name "parasoup-token")

(defn parse-cookies [cookies]
  (let [split (string/split cookies #"; ")]
    (into
     {}
     (map
      (fn [cookie-str]
        (let [split (string/split cookie-str #"=")
              k (first split)
              v (last split)]
          [k v ]))
      split))))

(defn parse-cookies-from-headers [headers]
  (when-let [cookies (get headers "cookie")]
    (when (string? cookies)
      (parse-cookies cookies))))

(defn parse-auth [auth-header]
  (let [parts (string/split auth-header #"Basic ")]
    (when (= 2 (count parts))
      (last parts))))

(defn parse-auth-response-from-headers [headers]
  (when-let [auth (get headers "authorization")]
    (when (string? auth)
      (parse-auth auth))))

(defn valid-token? [db token]
  (not (nil? (dbp/get-token db token))))

(defn form-auth-text [username password]
  (String. (b64/encode (.getBytes (str username ":" password)))))

(defn authenticated? [db username password request]
  (let [headers (:headers request)
        parsed-cookie (parse-cookies-from-headers headers)
        auth-response (parse-auth-response-from-headers headers)
        target-auth-text (form-auth-token username password)]
    (log/info (:headers request))
    (if (= auth-response target-auth-text)
      (log/info "auth success")
      (if-let [token (get parsed-cookie cookie-name)]
        (valid-token? db token)
        false))))

(defn send-auth-request [db response-channel]
  (as/go
   (as/>!
    response-channel
    {:status 401
     :body "Authorization required"
     :headers {"WWW-Authenticate" "Basic realm=\"Please log in\""}})))
