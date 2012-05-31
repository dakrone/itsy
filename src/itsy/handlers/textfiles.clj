(ns itsy.handlers.textfiles
  "Handler to index web pages into a directory of text files"
  (:require [clojure.java.io :refer [file]]
            [clojure.tools.logging :refer [info debug trace warn]]
            [clj-http.util :as util]))

(defn make-textfile-handler
  "Create a handler that saves urls into text files"
  [config]
  (let [directory (file (or (:directory config) "data"))
        extension (or (:extension config) "")
        file-for (fn file-for* [url]
                   (let [url (.replace url "http://" "")
                         url (.replace url "https://" "")]
                     (file directory (str (util/url-encode url) extension))))]
    (when-not (.exists directory)
      (info "Creating directory" (.getAbsolutePath directory))
      (.mkdir directory))
    (fn textfile-handler* [url-map body]
      (let [f (file-for (:url url-map))]
        (trace "writing" (.getAbsolutePath f))
        (spit f body)))))
