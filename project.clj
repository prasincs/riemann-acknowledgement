(defproject riemann-acknowledgement "0.1.3"
  :description "riemann acknowledgment plugin"
  :url "https://github.com/exoscale/riemann-acknowledgement"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :profiles {:dev {:dependencies [[riemann "0.2.10"]]}}
  :dependencies [[org.clojure/clojure       "1.7.0"]
                 [compojure                 "1.4.0"]
                 [ring/ring-json            "0.4.0"]
                 [http-kit "2.1.18"]
                 [javax.servlet/javax.servlet-api "3.1.0"]])
