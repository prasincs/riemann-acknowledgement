(ns riemann.plugin.acknowledgement
  (:require
   [org.httpkit.server :refer [run-server]]
   [compojure.route       :as route]
   [ring.middleware.json  :as json]
   [clojure.tools.logging :refer [info]]
   [compojure.core        :refer [defroutes GET POST PUT]]
   [ring.util.response    :refer [response redirect]]
   [riemann.streams       :refer [smap where*]]
   [riemann.config        :refer [service!]]
   [riemann.streams :as streams]
   [riemann.service       :refer [Service ServiceEquiv]]))

(def ^{:doc "Our acknowledgement database, stored as a set"}
  acks (atom #{}))

(defn conjack!
  "Add a member to the set of acknowledged host/service couples"
  [member]
  (swap! acks conj (vec member)))

(defn disjack!
  "Remove a member from the set of acknowledged host/service couples"
  [member]
  (swap! acks disj (vec member)))

(defn acked?
  [{:keys [tags] :as event}]
  (info "acked? " event "tags " tags)
  (let [tags (set tags)]
    (tags "acked")))

(defroutes api-routes
  "Our simple acknowledgement API with three resty routes.
   We use PUT instead of DELETE here to simplify client side work"
  (GET "/acks" []       (response @acks))
  (POST "/acks" request (response (conjack! (:body request))))
  (PUT  "/acks" request (response (disjack! (:body request))))

  (GET "/" []           (redirect "/index.html"))

  (route/resources      "/")
  (route/not-found      "<html><h2>404</h2></html>"))


(defn with-ack-status
  "Associate acknowledgement status to events"
  [& _]
  (fn stream [{:keys [host service tags] :as event}]
    (info "with-ack-status event:" event ", host:" host ", service:" service ", in acks: "(@acks [host service]))
    (try
      (if (acked? event)
        (do
          (info "already acked " event)
          event)
        (if (@acks [host service])
          (do (info "To be acked")
              (let [ev (update-in event [:tags] #(-> % (conj "acked") set))]
                (info "acked event" ev)
                ev))
          event))
      (catch Exception e
        (info "could not process acked status for: " event)
        event))))

(defn alert-stream
  "Given a function that sends out alerts to interested parties,
   only call that function on events which are not acknowledged.

   The single arity version sends non acked events to its first argument,
   assuming it is a stream function.

   The double arity version does the same and sends acked events to
   its second argument. This can be useful to index events."
  ([non-acked]
   (where* (complement acked?) non-acked))
  ([non-acked acked]
   (where* (complement acked?) non-acked (else acked))))

(defrecord AcknowledgementServer [host port headers core server]
  ServiceEquiv
  (equiv? [this other]
    (and (instance? AcknowledgementServer other)
         (= host (:host other))
         (= port (:port other))))
  Service
  (conflict? [this other]
    (and (instance? AcknowledgementServer other)
         (= host (:host other))
         (= port (:port other))))
  (reload! [this new-core]
    (reset! core new-core))
  (start! [this]
    (locking this
      (when-not @server
        (reset! server (run-server
                        (-> api-routes
                            (json/wrap-json-body)
                            (json/wrap-json-response))
                        {:host host :port port}))
        (info "acknowledgment server" host port "online"))))
  (stop! [this]
    (locking this
      (when @server
        (@server)
        (info "acknowledgement server" host port "shut down")))))

(defn acknowledgement-server
  "Start an acknowledgement server"
  ([]
     (acknowledgement-server {}))
  ([{:keys [host port headers]
     :or {host    "127.0.0.1"
          port    5559
          headers {}}}]
     (service!
      (AcknowledgementServer. host port headers (atom nil) (atom nil)))))
