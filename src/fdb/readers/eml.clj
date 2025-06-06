(ns fdb.readers.eml
  "Read .eml MIME message files, see https://en.wikipedia.org/wiki/MIME.
  Also splits .mbox files into .eml, see https://en.wikipedia.org/wiki/Mbox."
  (:refer-clojure :exclude [read])
  (:require
   [clojure-mail.core :as mail]
   [clojure-mail.message :as message]
   [clojure-mail.parser :as parser]
   [clojure.java.io :as io]
   [clojure.set :as set]
   [clojure.string :as str]
   [fdb.utils :as u]
   [fdb.readers.eml :as eml]
   [babashka.fs :as fs]))

(defn- try-msg->map
  [msg]
  {:content-type (.getContentType msg)
   :body         (try (.getContent msg)
                      (catch java.io.UnsupportedEncodingException _
                        "<unsupported encoding>")
                      (catch Throwable _
                        "<could not read body>"))})

(defn read-message
  "Like clojure-mail.message/read-message, but catches unsupported/unknown
  encoding exceptions while reading content."
  [message]
  (with-redefs [message/msg->map try-msg->map]
    (let [ret (message/read-message message)]
      (doall (:body ret))
      ret)))

(defn match-type
  ([part type]
   (some-> part :content-type str/lower-case (str/starts-with? type)))
  ([part type ignore]
   (when-some [content-type (some-> part :content-type str/lower-case)]
     (and (str/starts-with? content-type type)
          (not (str/includes? content-type ignore))))))

(defn prefer-text
  [parts]
  (if (and (seq? parts)
           (>= (count parts) 2))
    (let [maybe-plain (some #(when (match-type % "text/plain" "; name=") %) parts)
          maybe-html  (some #(when (match-type % "text/html") %) parts)]
      (if (and maybe-plain maybe-html)
        ;; looks like an alternative between plain and html, prefer plain
        (list maybe-plain)
        parts))
    parts))

(defn part->text
  [{:keys [content-type body] :as part}]
  (cond
    (nil? part)                            "<no text body>"
    (str/includes? content-type "; name=") (str "Attachment: " content-type "\n")
    (match-type part "text/plain")         body
    ;; With a nice html parser we could make it the preferred option.
    (match-type part "text/html")          (parser/html->text body)
    :else                                  (str "Attachment: " content-type "\n")))

;; TODO: use multipart/alternative in content-type to figure out which to take
(defn message-content
  [message-edn]
  (try (->> (:body message-edn)
            ;; for each list, prefer text if it looks like an alternative between plain and html
            (tree-seq seq? prefer-text)
            ;; lists are branches, we only care about the part leaves
            (remove seq?)
            (map part->text)
            (str/join "\n")
            u/unix-line-separators)
       (catch Throwable _
         "<could not read body>")))

(defn to-references-vector
  [references-str]
  (some->> references-str
           u/unix-line-separators
           str/trim
           str/split-lines
           (mapv str/trim)))

(defn read
  [{:keys [self-path]}]
  (let [message-edn  (-> self-path mail/file->message read-message)
        from-message (-> message-edn
                         (update :from #(mapv :address %))
                         (update :to #(mapv :address %))
                         (update :cc #(mapv :address %))
                         (update :bcc #(mapv :address %))
                         (update :sender :address)
                         (dissoc :multipart? :content-type :headers
                                 :body ;; writing it to content separately
                                 :id   ;; getting message-id from headers instead
                                 )
                         (set/rename-keys {:date-sent :date})) ;; match headers better
        headers      (->> message-edn
                          :headers
                          (apply merge)
                          (map (fn [[k v]]
                                 [(-> k str/lower-case keyword) v]))
                          (into {}))
        from-headers (-> headers
                         (select-keys [:message-id :references :in-reply-to :reply-to])
                         (update :references to-references-vector))
        from-body    {:text (message-content message-edn)}
        from-gmail   (-> headers
                         (select-keys [:x-gm-thrid :x-gmail-labels])
                         (set/rename-keys {:x-gm-thrid :thread-id
                                           :x-gmail-labels :labels})
                         (update :labels #(when % (str/split % #","))))]
    (u/strip-nil-empty
     (merge from-message from-headers from-body from-gmail))))

(comment
  (read {:self-path "./resources/eml/sample.eml"})
  ;;
  )
