(ns itsy.core
  (:require [cemerick.url :refer [url]]
            [clojure.string :as s]
            [clojure.tools.logging :refer [info debug trace]]
            [clj-http.client :as http]
            [slingshot.slingshot :refer [try+]])
  (:import (java.net URL)
           (java.util.concurrent LinkedBlockingQueue TimeUnit)))

(def terminated Thread$State/TERMINATED)

(defn valid-url? [url-str]
  (try
    (url url-str)
    (catch Exception _ nil)))

(defn- enqueue* [config url]
  (trace :enqueue-url url)
  (.put (-> config :state :url-queue)
        {:url url :count @(-> config :state :url-count)})
  (swap! (-> config :state :url-count) inc))


(defn enqueue-url
  "Enqueue the url assuming the url-count is below the limit and we haven't seen
  this url before."
  [config url]
  (if (get @(-> config :state :seen-urls) url)
    (swap! (-> config :state :seen-urls) update-in [url] inc)

    (when (or (neg? (:url-limit config))
              (< @(-> config :state :url-count) (:url-limit config)))
      (when-let [url-info (valid-url? url)]
        (swap! (-> config :state :seen-urls) assoc url 1)
        (if-let [host-limiter (:host-limit config)]
          (when (.contains (:host url-info) host-limiter)
            (enqueue* config url))
          (enqueue* config url))))))


(defn enqueue-urls [config urls]
  (doseq [url urls]
    (enqueue-url config url)))


(def url-regex #"https?://[-A-Za-z0-9+&@#/%?=~_|!:,.;]*[-A-Za-z0-9+&@#/%=~_|]")

(defn extract-urls [body]
  (when body
    (re-seq url-regex body)))

(defn crawl-page [config url-map]
  (try+
    (trace :retrieving-body-for url-map)
    (let [url (:url url-map)
          score (:count url-map)
          body (:body (http/get url (:http-opts config)))
          _ (trace :extracting-urls)
          urls ((:url-extractor config) body)]
      (enqueue-urls config urls)
      ((:handler config) url-map body))
    (catch Object _
      (trace :blurgh!))))


(defn thread-status [config]
  (zipmap (map (memfn getId) @(-> config :state :running-workers))
          (map (memfn getState) @(-> config :state :running-workers))))


(defn worker-fn
  "Generate a worker function for a config object."
  [config]
  (fn worker-fn* []
    (loop []
      (trace :waiting-for-url)
      (when-let [url-map (.poll (-> config :state :url-queue)
                                3 TimeUnit/SECONDS)]
        (trace :got url-map)
        (crawl-page config url-map))
      (let [tid (.getId (Thread/currentThread))]
        (trace :running? (get @(-> config :state :worker-canaries) tid))
        (when (get @(-> config :state :worker-canaries) tid)
          (recur))))))


(defn start-worker
  "Start a worker thread for a config object, updating the config's state with
  the new Thread object."
  [config]
  (let [w-thread (Thread. (worker-fn config))
        w-tid (.getId w-thread)]
    (swap! (-> config :state :worker-canaries) assoc w-tid true)
    (swap! (-> config :state :running-workers) conj w-thread)
    (println :starting w-thread w-tid)
    (.start w-thread))
  (println "New worker count:" (count @(-> config :state :running-workers))))


(defn stop-workers
  "Given a config object, stop all the workers for that config."
  [config]
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
  (trace :options options)
  (let [hl (:host-limit options)
        host-limiter (cond
                       (string? hl) (try (:host (url hl))
                                         (catch Exception _
                                           (trace :eh?)
                                           hl))
                       (= true hl) (:host (url (:url options)))
                       :else hl)
        _ (trace :host-limiter host-limiter)
        config (merge {:workers 5
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
                      options
                      {:host-limit host-limiter})]
    (trace :config config)
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
