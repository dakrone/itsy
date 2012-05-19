(ns itsy.core
  (:require [cemerick.url :refer [url]]
            [clojure.string :as s]
            [clojure.tools.logging :refer [info debug trace]]
            [clj-http.client :as http]
            [slingshot.slingshot :refer [try+]])
  (:import (java.net URL)
           (java.util.concurrent LinkedBlockingQueue TimeUnit)))

(def terminated Thread$State/TERMINATED)

;; Debugging helpers
(def trace-on true)
(defn pdbg [& args]
  (when trace-on
    (apply println (str "[" (.getId (Thread/currentThread)) "]->") args)))


(defn valid-url? [url]
  (try
    (http/parse-url url)
    (catch Exception _ nil)))


(defn enqueue-url
  "Enqueue the url assuming the url-count is below the limit and we haven't seen
  this url before."
  [config url]
  (when (and (not (get @(-> config :state :seen-urls) url))
             (or (neg? (:url-limit config))
                 (< @(-> config :state :url-count) (:url-limit config))))
    (when-let [url-info (valid-url? url)]
      (pdbg :enqueue-url url)
      (swap! (-> config :state :seen-urls) assoc url true)
      (.put (-> config :state :url-queue)
            {:url url :count @(-> config :state :url-count)})
      (swap! (-> config :state :url-count) inc))))


(defn enqueue-urls [config urls]
  (doseq [url urls]
    (enqueue-url config url)))


(def url-regex #"https?://[-A-Za-z0-9+&@#/%?=~_|!:,.;]*[-A-Za-z0-9+&@#/%=~_|]")

(defn extract-urls [body]
  (when body
    (re-seq url-regex body)))

(defn crawl-page [config url-map]
  (try+
    (pdbg :retrieving-body-for url-map)
    (let [url (:url url-map)
          score (:count url-map)
          body (:body (http/get url (:http-opts config)))
          _ (pdbg :extracting-urls)
          urls ((:url-extractor config) body)]
      (enqueue-urls config urls)
      ((:handler config) url-map body))
    (catch Object _
      (pdbg :blurgh!))))


(defn thread-status [config]
  (zipmap (map (memfn getId) @(-> config :state :running-workers))
          (map (memfn getState) @(-> config :state :running-workers))))


(defn worker-fn [config]
  (fn worker-fn* []
    (loop []
      (pdbg :waiting-for-url)
      (when-let [url-map (.poll (-> config :state :url-queue)
                                3 TimeUnit/SECONDS)]
        (pdbg :got url-map)
        (crawl-page config url-map))
      (let [tid (.getId (Thread/currentThread))]
        (pdbg :running? (get @(-> config :state :worker-canaries) tid))
        (when (get @(-> config :state :worker-canaries) tid)
          (recur))))))


(defn start-worker [config]
  (let [w-thread (Thread. (worker-fn config))
        w-tid (.getId w-thread)]
    (swap! (-> config :state :worker-canaries) assoc w-tid true)
    (swap! (-> config :state :running-workers) conj w-thread)
    (println :starting w-thread w-tid)
    (.start w-thread))
  (println "New worker count:" (count @(-> config :state :running-workers))))


(defn stop-workers [config]
  (println "Strangling canaries...")
  (reset! (-> config :state :worker-canaries) {})
  (println "Waiting for workers to finish...")
  (map #(.join % 30000) @(-> config :state :running-workers))
  (Thread/sleep 10000)
  (if (= #{terminated} (set (vals (thread-status config))))
    (do
      (println "All workers stopped.")
      (reset! (-> config :state :running-workers) []))
    (do
      (println "Unable to stop all workers.")
      (reset! (-> config :state :running-workers)
              (remove #(= terminated (.getState %))
                      @(-> config :state :running-workers)))))
  @(-> config :state :running-workers))


(defn crawl
  "Crawl a url with the given worker count and handler."
  [options]
  (pdbg :options options)
  (let [config (merge {:workers 5
                       :url-limit 100
                       :url-extractor extract-urls
                       :state {:url-queue (LinkedBlockingQueue.)
                               :url-count (atom 0)
                               :running-workers (atom [])
                               :worker-canaries (atom {})
                               :seen-urls (atom {})}
                       :http-opts {:socket-timeout 10000
                                   :conn-timeout 10000
                                   :insecure? true}}
                      options)]
    (pdbg :config config)
    (println "Starting workers...")
    (http/with-connection-pool {:timeout 5
                                :threads (:workers config)
                                :insecure? true}
      (dotimes [_ (:workers config)]
        (start-worker config))
      (println "Starting crawl of" (:url config))
      (enqueue-url config (:url config)))
    config))

(defn palm
  "It's a HAND-ler.. get it? That was a terrible pun."
  [url-map body]
  (println :url (:url url-map) :size (count body)))
