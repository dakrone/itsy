# Itsy

A threaded web spider, written in Clojure.

## Usage

In your project.clj:

```clojure
[itsy "0.1.0"]
```

In your project:

```clojure
(ns myns.foo
  (:require [itsy.core :refer :all]))

(defn my-handler [url-map page-body]
  (println (:url url-map) "has a count of" (count page-body)))

(def c (crawl {;; initial URL to start crawling at (required)
               :url "http://aoeu.com"
               ;; handler to use for each page crawled (required)
               :handler my-handler
               ;; number of threads to use for crawling, (optional,
               ;; defaults to 5)
               :workers 10
               ;; number of urls to spider before crawling stops, note
               ;; that workers must still be stopped after crawling
               ;; stops. May be set to -1 to specify no limit.
               ;; (optional, defaults to 100)
               :url-limit 100
               ;; function to use to extract urls from a page, a
               ;; function that takes one argument, the body of a page.
               ;; (optional, defaults to itsy's extract-all)
               :url-extractor extract-all
               ;; http options for clj-http, (optional, defaults to
               ;; {:socket-timeout 10000 :conn-timeout 10000 :insecure? true})
               :http-opts {}
               ;; specifies whether to limit crawling to a single
               ;; domain. If false, does not limit domain, if true,
               ;; limits to the same domain as the original :url, if set
               ;; to a string, limits crawling to the hostname of the
               ;; given url
               :host-limit false}))

;; ... crawling ensues ...

(thread-status c)
;; returns a map of thread-id to Thread.State:
{33 #<State RUNNABLE>, 34 #<State RUNNABLE>, 35 #<State RUNNABLE>,
 36 #<State RUNNABLE>, 37 #<State RUNNABLE>, 38 #<State RUNNABLE>,
 39 #<State RUNNABLE>, 40 #<State RUNNABLE>, 41 #<State RUNNABLE>,
 42 #<State RUNNABLE>}

(add-worker c)
;; adds an additional thread worker to the pool

(remove-worker c)
;; removes a worker from the pool

(stop-workers c)
;; stop-workers will return a collection of all threads it failed to
;; stop (it should be able to stop all threads unless something goes
;; very wrong)
```

Upon completion, `c` will contain state that allows you to see what
happened:

```clojure
(clojure.pprint/pprint (:state c))
;; URLs still in the queue
{:url-queue #<LinkedBlockingQueue []>,
;; URLs that were seen/queued
 :url-count #<Atom@67d6b87e: 2>,
 ;; running worker threads (will contain thread objects while crawling)
 :running-workers #<Ref@decdc7b: []>,
 ;; canaries for running worker threads
 :worker-canaries #<Ref@397f1661: {}>,
 ;; a map of URL to times seen/extracted from the body of a page
 :seen-urls
 #<Atom@469657c4:
   {"http://www.phpbb.com" 1,
    "http://pagead2.googlesyndication.com/pagead/show_ads.js" 2,
    "http://www.subBlue.com/" 1,
    "http://www.phpbb.com/" 1,
    "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd" 1,
    "http://www.w3.org/1999/xhtml" 1,
    "http://forums.asdf.com" 1,
    "http://www.google.com/images/poweredby_transparent/poweredby_000000.gif" 1,
    "http://asdf.com" 1,
    "http://www.google.com/cse/api/branding.css" 1,
    "http://www.google.com/cse" 1}>}
```

## Features
- Multithreaded, with the ability to add and remove workers as needed
- No global state, run multiple crawlers with multiple threads at once
- Pre-written handlers for text files and ElasticSearch
- Skips URLs that have been seen before
- Domain limiting to crawl pages only belonging to a certain domain

## Included handlers

Itsy includes handlers for common actions, either to be used, or
examples for writing your own.

### Text file handler

The text file handler stores web pages in text files. It uses the
`html->str` method in `itsy.extract` to convert HTML documents to
plain text (which in turn uses [Tika](http://tika.apache.org) to
extract HTML to plain text).

Usage:

```clojure
(ns bar
  (:require [itsy.core :refer :all]
            [itsy.handlers.textfiles :refer :all]))

;; The directory will be created when the handler is created if it
;; doesn't already exist
(def txt-handler (make-textfile-handler {:directory "/mnt/data" :extension ".txt"}))

(def c (crawl {:url "http://example.com" :handler txt-handler}))

;; then look in the /mnt/data directory
```

### [ElasticSearch](http://elasticsearch.org) handler

The elasticsearch handler stores documents with the following mapping:

```clojure
{:id {:type "string"
      :index "not_analyzed"
      :store "yes"}
 :url {:type "string"
       :index "not_analyzed"
       :store "yes"}
 :body {:type "string"
        :store "yes"}}
```

Usage:

```clojure
(ns foo
  (:require [itsy.core :refer :all]
            [itsy.handlers.elasticsearch :refer :all]))

;; These are the default settings
(def index-settings {:settings
                     {:index
                      {:number_of_shards 2
                       :number_of_replicas 0}}})

;; If the ES index doesn't exist, make-es-handler will create it when called.
(defn es-handler (make-es-handler {:es-url "http://localhost:9200/"
                                   :es-index "crawl"
                                   :es-type "page"
                                   :es-index-settings index-settings
                                   :http-opts {}}))

(def c (crawl {:url "http://example.com" :handler es-handler}))

;; ... crawling and indexing ensues ...
```


## Todo

- <del>Relative URL extraction/crawling</del>
- Always better URL extraction
- Handlers for common body actions
  - <del>elasticsearch</del>
  - <del>text files</del>
  - other?
- <del>Helpers for dynamically raising/lowering thread count</del>
- Timed crawling, have threads clean themselves up after a limit
- <del>Have threads auto-clean when url-limit is hit</del>
- <del>Use Tika for HTML extraction</del>
- Write tests

## License

Copyright © 2012 Lee Hinman

Distributed under the Eclipse Public License, the same as Clojure.
