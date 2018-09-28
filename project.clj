(defproject yummly/logentries-timbre-appender "0.1.3"
  :description "Timbre appender for logentries.com"
  :url "http://example.com/FIXME"
  :license {:name         "Eclipse Public License"
            :url          "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments     "Same as Clojure & timbre"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [com.taoensso/timbre "4.10.0"]
                 [cheshire "5.8.0"]
                 [io.aviso/pretty     "0.1.33"]]
  :profiles {:1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0"]]}}
  :global-vars {*warn-on-reflection* true
                *assert*             true}
  :aliases {"test-all" ["with-profile" "+1.7:+1.8:+1.9" "test"]})
