(ns clj-parasoup.util.async
  (:require [clojure.core.async :as as]
            [clojure.core.async.impl.channels :as cimpl])
  (:import clojure.core.async.impl.channels.ManyToManyChannel))

(defn chan? [ch] (instance? clojure.core.async.impl.channels.ManyToManyChannel ch))
