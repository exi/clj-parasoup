(def tk-version "0.4.2")
(def ks-version "0.7.2")

(defproject clj-parasoup "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/core.async "0.1.303.0-886421-alpha"]
                 [org.clojure/tools.logging "0.2.6"]
                 [http-kit "2.1.16"]
                 [puppetlabs/trapperkeeper ~tk-version :classifier "test"]
                 [lein-light-nrepl "0.0.18"]
                 [aleph "0.3.2"]
                 [org.apache.hbase/hbase-client "0.98.3-hadoop2"]
                 [org.apache.hbase/hbase-common "0.98.3-hadoop2"]
                 [org.clojure/data.codec "0.1.0"]
                 [com.taoensso/nippy "2.6.3"]
                 [digest "1.4.4"]
                 [commons-io/commons-io "2.4"]
                 [org.clojure/data.json "0.2.5"]]

  :test-paths ["test" "test-resources"]

  :profiles {:dev {:dependencies [[puppetlabs/trapperkeeper-webserver-jetty9 "0.3.3"]
                                  [puppetlabs/trapperkeeper ~tk-version :classifier "test" :scope "test"]
                                  [puppetlabs/kitchensink ~ks-version :classifier "test" :scope "test"]
                                  [clj-http "0.7.9"]
                                  [org.clojure/tools.trace "0.7.8"]
                                  [ring-mock "0.1.5"]]}
             :uberjar {:aot [puppetlabs.trapperkeeper.main]}}

  :aliases {"tk" ["trampoline" "run" "--config" "resources/config.ini" "--bootstrap-config" "resources/bootstrap.cfg"]}

  :plugins [[lein-swank "1.4.5"]
            [lein-sub "0.3.0"]]

  :repl-options {:nrepl-middleware [lighttable.nrepl.handler/lighttable-ops]}

  :main puppetlabs.trapperkeeper.main)
