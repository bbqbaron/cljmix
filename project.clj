(defproject cljmix "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :jvm-opts ["-Xmx5g"]
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [clj-http "3.9.1"]
                 [cheshire "5.8.0"]
                 [com.stuartsierra/component "0.3.2"]
                 [com.walmartlabs/lacinia-pedestal "0.13.0"]
                 [io.aviso/logging "0.2.0"]
                 [org.clojure/clojurescript "1.10.339"]
                 [re-frame "0.10.5"]
                 [re-graph "0.1.5"]
                 [prevayler-clj "3.0.1"]
                 [vincit/venia "0.2.5"]]
  :plugins [[lein-cljsbuild "1.1.7"]]
  :test-paths ["test/clj"]
  :source-paths ["src/clj" "src/cljc"]
  :profiles
  {:dev
   {:plugins
    [[com.jakemccrary/lein-test-refresh "0.23.0"]]
    :dependencies
    [[org.clojure/tools.namespace "0.2.11"]
     [com.stuartsierra/component.repl "0.2.0"]]}}
  :main cljmix.main
  :cljsbuild
  {
   :builds
   {:client {
             :source-paths ["src/cljs" "src/cljc"]
             :compiler     {:main       cljmix.core
                            :asset-path "cljs/out"
                            :output-to  "resources/public/cljs/main.js"
                            :output-dir "resources/public/cljs/out"}}}})
