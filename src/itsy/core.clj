(ns itsy.core
  (:require [clojure.string :as s]
            [clj-http.client :as http]
            [slingshot.slingshot :refer [try+]])
  (:import (java.net URL)
           (java.util.concurrent LinkedBlockingQueue TimeUnit)))

(def terminated Thread$State/TERMINATED)

;; Queue for URLs to be worked on
(def url-queue (LinkedBlockingQueue.))

;; Map of URLs we've already seen/spidered
(def seen-urls (atom {}))

;; List of running worker threads
(def running-workers (atom []))

;; Canaries that keep worker threads alive
(def worker-canaries (atom {}))

;; Current count of seen urls
(def url-count (atom 0))

;; How many urls to spider before stopping
(def url-limit 3000)


;; Debugging helpers
(def trace true)
(defn pdbg [& args]
  (when trace
    (apply println (str "[" (.getId (Thread/currentThread)) "]->") args)))


(defn valid-url? [url]
  (try
    (http/parse-url url)
    (catch Exception _ nil)))


(defn enqueue-url
  "Enqueue the url assuming the url-count is below the limit and we haven't seen
  this url before."
  [url]
  (when (and (< @url-count url-limit)
             (not (get @seen-urls url)))
    (when-let [url-info (valid-url? url)]
      (pdbg :enqueue-url url)
      (swap! seen-urls assoc url true)
      (.put url-queue {:url url :count @url-count})
      (swap! url-count inc))))


(defn enqueue-urls [urls]
  (doseq [url urls]
    (enqueue-url url)))


(def url-regex #"https?://[-A-Za-z0-9+&@#/%?=~_|!:,.;]*[-A-Za-z0-9+&@#/%=~_|]")

(defn extract-urls [body]
  (when body
    (re-seq url-regex body)))

(defn crawl-page [url-map handler]
  (try+
    (pdbg :retrieving-body-for url-map)
    (let [url (:url url-map)
          score (:count url-map)
          body (-> (http/get url
                             {:socket-timeout 10000
                              :conn-timeout 10000
                              :insecure? true})
                   :body)
          _ (pdbg :extracting-urls)
          urls (extract-urls body)]
      (enqueue-urls urls)
      (handler url-map body))
    (catch Object _
      (pdbg :blurgh!))))


(defn thread-status []
  (zipmap (map (memfn getId) @running-workers)
          (map (memfn getState) @running-workers)))


(defn worker-fn [handler]
  (fn worker-fn* []
    (loop []
      (pdbg :waiting-for-url)
      (when-let [url-map (.poll url-queue 3 TimeUnit/SECONDS)]
        (pdbg :got url-map)
        (crawl-page url-map handler))
      (let [tid (.getId (Thread/currentThread))]
        (pdbg :running? (get @worker-canaries tid))
        (when (get @worker-canaries tid)
          (recur))))))


(defn start-worker [handler]
  (let [w-thread (Thread. (worker-fn handler))
        w-tid (.getId w-thread)]
    (swap! worker-canaries assoc w-tid true)
    (swap! running-workers conj w-thread)
    (println :starting w-thread w-tid)
    (.start w-thread))
  (println "New worker count:" (count @running-workers)))


(defn stop-workers []
  (println "Strangling canaries...")
  (reset! worker-canaries {})
  (println "Waiting for workers to finish...")
  (map #(.join % 30000) @running-workers)
  (Thread/sleep 10000)
  (if (= #{terminated} (set (vals (thread-status))))
    (do
      (println "All workers stopped.")
      (reset! running-workers []))
    (do
      (println "Unable to stop all workers.")
      (reset! running-workers
              (remove #(= terminated (.getState %)) @running-workers))))
  @running-workers)


(defn crawl
  "Crawl a url with the given worker count and handler."
  [url worker-count handler]
  (println "Resetting URL count to 0.")
  (reset! url-count 0)
  (println "Resetting seen URL map.")
  (reset! seen-urls {})
  (println "Starting workers...")
  (http/with-connection-pool {:timeout 5 :threads worker-count :insecure? true}
    (dotimes [_ worker-count]
      (start-worker handler))
    (println "Starting crawl of" url)
    (enqueue-url url))
  nil)

(defn palm
  "It's a HAND-ler.. get it? That was a terrible pun."
  [url-map body]
  (println :url (:url url-map) :size (count body)))
