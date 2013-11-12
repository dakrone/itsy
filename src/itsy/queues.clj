(ns itsy.queues
  "Maintain queues that enforce delay policies."
  (:require [cemerick.url :refer [url]]
            [clojure.tools.logging :refer [trace]])
  (:import (java.util.concurrent LinkedBlockingQueue)))

(def *host-last-request* (atom {})) ;record the timestamp of the last
                                        ;request made to a host
(def *host-urls-queue* (atom {}))   ;maintains a queue per each host
(def *ready-queue* (LinkedBlockingQueue.))
(def *host-waiting-queue* (atom {})) ; keep track of a host's url
                                     ; waiting to be requested. Next
                                     ; url enqueued only when no other
                                     ; requests to a host are pending

(defn default-delay-policy
  "Waits 3 seconds before making the next request.
We expect a timestamp to be milliseconds since epoch"
  [a-host timestamp]
  (if-let [last-hit-ts (@*host-last-request* a-host)]
    (< 3000 (- timestamp last-hit-ts))
    true))

(defn setup-new-host
  [a-host]
  (swap! *host-urls-queue* merge {a-host (LinkedBlockingQueue.)})
  (swap! *host-waiting-queue* merge {a-host false}))

(defn enqueue*
  [config a-url]
  (let [processed-url (url a-url)
        the-host      (-> processed-url :host)]
    (if-let [host-queue (@*host-urls-queue* the-host)]
      (.put host-queue {:url   a-url
                        :count (-> config :state :url-count)})
      (do
        (setup-new-host the-host)
        (recur config a-url)))))

(defn ready?
  "Delay policy must be enforced and the host's
queue must not be waiting on a request"
  [a-host]
  (and (default-delay-policy a-host (System/currentTimeMillis))
       (not (@*host-waiting-queue* a-host))))

(defn url-queue-worker-fn
  []
  (doseq [[host host-queue] @*host-urls-queue*]
    (when (ready? host)
     (when-let [next-url (.poll host-queue)]
       (trace :next-url-ready (format "%d-%s" (System/currentTimeMillis) next-url))
       (.put *ready-queue* next-url)
       (swap! *host-waiting-queue* merge {host true}))))
  (recur))

(defn start-url-queue-worker
  []
  (let [url-thread (Thread. url-queue-worker-fn)]
    (.start url-thread)))
