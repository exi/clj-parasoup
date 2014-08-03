(ns clj-parasoup.util.header-parser
  (:require [clojure.string :as string]))

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

