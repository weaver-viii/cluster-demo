(ns immutant.init
  (:require immutant.web
            [immutant.cache     :as cache]
            [immutant.messaging :as messaging]
            [immutant.daemons   :as daemon])
  (:use [ring.middleware.resource :only [wrap-resource]]
        [ring.util.response :only [response]]
        [clojure.pprint :only [pprint]]))

;;; Create a message queue
(messaging/start "/queue/msg")

;;; Define a consumer for our queue
(def listener (messaging/listen "/queue/msg" #(println "received:" %)))

;;; Create a distributed cache to hold our counter value
(def cache (cache/lookup-or-create "counters"))

;;; Controls the state of our daemon
(def done (atom false))

;;; Our daemon's start function
(defn start []
  (reset! done false)
  (while (not @done)
    (Thread/sleep 5000)
    (let [i (:value cache 1)]
      (println "sending:" i)
      (messaging/publish "/queue/msg" i)
      (cache/put cache :value (inc i)))))

;;; Our daemon's stop function
(defn stop []
  (reset! done true))

;;; Register the daemon
(daemon/daemonize "counter" start stop)

(defn handler [request]
  (let [v (:value cache)]
    (println (format "web [%s]: %s" (:path-info request) v))
    (response (format "Cached value: %s" v))))

(immutant.web/start handler)