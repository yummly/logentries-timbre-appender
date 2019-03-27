(ns yummly.logentries-timbre-appender
  "Appender that sends output to Logentries (https://logentries.com/). Based of the logstash 3rd party appender.
   Requires Cheshire (https://github.com/dakrone/cheshire)."
  {:author "Ryan Smith (@tanzoniteblack), Mike Sperber (@mikesperber), David Frese (@dfrese)"}
  (:require [cheshire.core :as cheshire]
            [io.aviso.exception]
            [clojure.string]
            [pool.core :as pool])
  (:import [java.net Socket InetAddress]
           [java.io PrintWriter Closeable Writer]
           [javax.net.ssl SSLSocketFactory]))

(defn connect
  [host port ssl?]
  (let [addr (InetAddress/getByName host)
        sock (if ssl?
               (.createSocket (SSLSocketFactory/getDefault) addr (int port))
               (Socket. addr (int port)))]
    [sock
     (PrintWriter. (.getOutputStream sock))]))

(defn connection-ok?
  [[^Socket sock ^PrintWriter out]]
  (and (not (.isClosed sock))
       (.isConnected sock)
       (not (.checkError out))))

(def iso-format "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

(def stack-trace-processor (comp (remove :omitted)
                                 (map (fn [stack-frame]
                                        (format "%s (%s:%s)"
                                                (:formatted-name stack-frame)
                                                (:file stack-frame)
                                                (:line stack-frame))))))

(defn error-to-stacktrace
  "Create a tersely formatted vector of stack traces. This will show up in a
  nicely computationally searchable fashion in the json sent to logentries"
  [e]
  (when e
    (binding [io.aviso.exception/*fonts* nil]
      (let [errors (io.aviso.exception/analyze-exception e {})]
        (try (mapv #(-> %
                        (update :stack-trace (fn [stack-trace]
                                               (into [] stack-trace-processor stack-trace)))
                        ;; :properties can contain values unknown to
                        ;; cheshire, so just drop it if it exists so
                        ;; the log message is less likely to fail to
                        ;; be converted to json
                        (dissoc :properties))
                   errors)
             (catch Exception e
               (remove :omitted errors)))))))

(defn data->json-stream
  [data writer user-tags stacktrace-fn]
  ;; Note: this it meant to target the logstash-filter-json; especially "message" and "@timestamp" get a special meaning there.
  (cheshire/generate-stream
   (merge user-tags
          (:context data)
          {:level       (:level data)
           :namespace   (:?ns-str data)
           :file        (:?file data)
           :line        (:?line data)
           :stacktrace  (stacktrace-fn (:?err data))
           :hostname    (force (:hostname_ data))
           :message     (force (:msg_ data))
           "@timestamp" (:instant data)})
   writer
   {:date-format iso-format
    :pretty      false}))

(defn logentries-appender
  "Returns a Logentries appender, which will send each event in JSON format to the
  logentries server. Set `:flush?` to true to flush the writer after every
  event. If you wish to send additional, custom tags, to logentries on each
  logging event, then provide a hash-map in the opts `:user-tags` which will be
  merged into each event.

  Defaults to sending logs to logentries, but the URL data is sent to can be overwritten
  via `:log-ingest-url` and `:log-ingest-port` to send to any other service that works in
  the format `<TOKEN> MESSAGE>`, like datadog."
  [token & [opts]]
  (let [conn            (atom nil)
        flush?          (or (:flush? opts) false)
        nl              "\n"
        token           (str token " ")
        stacktrace-fn   (:stack-trace-fn opts error-to-stacktrace)
        log-ingest-url  (:log-ingest-url opts "data.logentries.com")
        log-ingest-port (:log-ingest-port opts 80)
        ssl?            (:ssl? opts false)]
    (let [cp (pool/get-pool
              #(connect log-ingest-url log-ingest-port ssl?)
              :validate connection-ok?
              :destroy (fn [[^Closeable socket ^Closeable writer]]
                         (try
                           (some-> writer .close)
                           (some-> socket .close)
                           (catch Exception _ nil))))]
      {:enabled?   true
       :async?     false
       :min-level  nil
       :rate-limit nil
       :output-fn  :inherit
       :fn
       (fn [data]
         (try
           (let [pooled (pool/borrow cp)
                 [sock ^Writer out] pooled]
             (when out
               (try
                 (.write out token)
                 (try (data->json-stream data out (:user-tags opts) stacktrace-fn)
                      (finally
                        ;; logstash tcp input plugin: "each event is assumed to be one line of text".
                        (.write out nl)
                        (when flush? (.flush out))))
                 (finally
                   (pool/return cp pooled)))))
           nil
           (catch java.io.IOException _
             nil)))})))
