(ns clj-parasoup.core
  (:require [puppetlabs.trapperkeeper.core :as tkc]))

(defn start []
  (let [args ["--config" "resources/config.ini" "--bootstrap-config" "resources/bootstrap.cfg"]]
    (apply (resolve 'puppetlabs.trapperkeeper.core/main) args)))
