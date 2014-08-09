(ns clj-parasoup.util.util)

(defn extract-gif-uri-from-url [url]
  (last (re-find #"http://asset-[^/]+(.*\.gif)" url)))
