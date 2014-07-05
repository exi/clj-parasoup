(ns clj_parasoup.http.service_test
  (:require [clojure.test :refer :all]
            [clojure.core.async :as as]
            [puppetlabs.trapperkeeper.testutils.bootstrap :refer [with-app-with-config]]
            [puppetlabs.trapperkeeper.app :refer [get-service]]
            [clj-http.client :as http]
            [clj-parasoup.http.service :refer :all]))

(deftest test-http-service
  (testing "An http service with test answers requests"
    (with-app-with-config app
      [aleph-http-service]
      {:aleph-http {:port 7888
              :host "127.0.0.1"}}
      (let [test-response {:status 200
                           :headers {"content-type" "text/plain"}
                           :body "test"}
            serv (get-service app :HttpService)]
        (set-request-handler serv (fn [chan req] (as/go (as/>! chan test-response))))
        (is (= test-response
               (let [req (http/get "http://127.0.0.1:7888")]
                 (reduce (fn [acc k] (assoc acc k (k test-response))) {} (keys test-response)))))))))
