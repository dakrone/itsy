# Itsy

A threaded web spider, written in Clojure.

## Usage

In your project.clj:

```clojure
[itsy "not-released-yet"]
```

In your project:

```clojure
(require [itsy.core :refer :all])

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
               ;; (optional, defaults to itsy's extract-urls)
               :url-extractor extract-urls
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
 :running-workers #<Atom@decdc7b: []>,
 ;; canaries for running worker threads
 :worker-canaries #<Atom@397f1661: {}>,
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

## Todo

- Relative URL extraction
- Handlers for common body actions
- Helpers for dynamically raising/lowering thread count

## License

Copyright © 2012 Lee Hinman

Distributed under the Eclipse Public License, the same as Clojure.
