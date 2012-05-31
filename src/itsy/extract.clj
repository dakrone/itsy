(ns itsy.extract
  (:import (java.io ByteArrayInputStream)
           (org.apache.tika.sax BodyContentHandler)
           (org.apache.tika.metadata Metadata)
           (org.apache.tika.parser ParseContext)
           (org.apache.tika.parser.html HtmlParser)))

(defn html->str
  "Convert HTML to plain text using Apache Tika"
  [body]
  (when body
    (let [bais (ByteArrayInputStream. (.getBytes body))
          handler (BodyContentHandler.)
          metadata (Metadata.)
          parser (HtmlParser.)]
      (.parse parser bais handler metadata (ParseContext.))
      (.toString handler))))
