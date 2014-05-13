(defproject cdtrepl.extension "0.1.0-SNAPSHOT"
  :description    "ClojureScript REPL for Chrome DevTools"
  :license {
      :name "Eclipse Public License"
      :url  "http://www.eclipse.org/legal/epl-v10.html"
  }
  
  :plugins [
     [lein-cljsbuild "1.0.1"]
  ]

  :hooks  [leiningen.cljsbuild]

  :dependencies [
  	[org.clojure/clojure "1.5.1"]
  	[org.clojure/clojurescript "0.0-2173"]
    [org.clojure/core.async "0.1.267.0-0d7780-alpha"]
    [jarohen/chord "0.3.1"]
    [alandipert/storage-atom "1.2.2"]
    [reagent "0.4.2"]
    [om "0.6.2"]]


  :cljsbuild {:builds  [{:id "dev"
                         :source-paths ["src/cljs"]
                         :compiler {:output-dir "extension/js/compiled"
                                    :output-to  "extension/js/compiled/cdtrepl.js"
                                    :source-map "extension/js/compiled/cdtrepl.js.map"
                                    :optimizations :none
                                    :pretty-print true}}]}
)
