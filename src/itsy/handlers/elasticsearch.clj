(ns itsy.handlers.elasticsearch
  "Handler to index web pages into elasticsearch"
  (:require [clojure.tools.logging :refer [info debug trace warn]]
            [clj-http.client :as http]
            [cheshire.core :as json]
            [slingshot.slingshot :refer [try+]])
  (:import (java.security MessageDigest)))

(def md (MessageDigest/getInstance "MD5"))

(defn md5
  "String MD5 sum"
  [s]
  (.toString (BigInteger. 1 (.digest md (.getBytes s))) 16))

(defn make-es-handler
  "Create an Itsy handler that will index document bodies into ElasticSearch"
  [config]
  (let [default {:es-url "http://localhost:9200/"
                 :es-index "crawl"
                 :es-type "page"
                 :es-index-settings {:settings {:index {:number_of_shards 2
                                                        :number_of_replicas 0}}}
                 :http-opts {:throw-entire-response? true}}
        new-config (merge default config)
        {:keys [es-url es-index es-type es-index-settings http-opts]} new-config
        mapping {:mappings {(keyword es-type)
                            {:_source {:enabled false}
                             :properties {:id {:type "string"
                                               :index "not_analyzed"
                                               :store "yes"}
                                          :url {:type "string"
                                                :index "not_analyzed"
                                                :store "yes"}
                                          :body {:type "string"
                                                 :store "yes"}}}}}
        creation-settings (merge es-index-settings mapping)]
    (when (try+ (http/head (str es-url es-index)) false
                (catch [:status 404] _ :create-new-index))
      (info "Creating index" es-index "with:" creation-settings)
      (http/post (str es-url es-index)
                 (merge http-opts {:body (json/encode creation-settings)})))
    (fn es-handler* [{:keys [url body]}]
      (let [id (md5 url)]
        (trace "indexing" id "->" url (str "[" (count body) "]"))
        (http/put (str es-url es-index "/" es-type "/" id)
                  (merge http-opts
                         {:body (json/encode {:id id :url url :body body})}))
        nil))))


