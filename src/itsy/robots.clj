(ns itsy.robots
  (:require [cemerick.url    :as url]
            [clj-http.client :as client]
            [clj-robots.core :as robots]))

(def host-robots (atom {}))

(defn fetch-robots
  [a-host]
  (try (-> a-host
           (url/url a-host "/robots.txt")
           str
           client/get
           :body
           robots/parse)
       (catch Exception e :not-found)))

(defn fetch-and-save-robots
  [a-site]
  (let [directives (fetch-robots a-site)]
    (swap! host-robots merge {(str
                               (-> a-site
                                   url/url
                                   :host))
                              directives})))

(defn crawlable?
  [link]
  (let [the-host (-> link
                     url/url
                     :host)
        the-path (-> link
                     url/url
                     :path)]
    (cond
      (and (@host-robots the-host)
           (not (= (@host-robots the-host)
                   :not-found)))
      (robots/crawlable? (@host-robots the-host) the-path)

      (and (@host-robots the-host)
           (= (@host-robots the-host)
              :not-found))
      true

      :else
      (do
        (fetch-and-save-robots link)
        (recur link)))))
