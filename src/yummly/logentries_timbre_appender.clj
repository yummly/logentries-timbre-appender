(ns yummly.logentries-timbre-appender
  "Appender that sends output to Logentries (https://logentries.com/).
   Requires Cheshire (https://github.com/dakrone/cheshire)."
  {:author "Ryan Smith (@tanzoniteblack), Vadim Geshel (@vgeshel)"}
  (:require [cheshire.core :as cheshire]
            [io.aviso.exception]
            [clojure.string :as s])
  (:import [com.rapid7.net AsyncLogger LoggerConfiguration$Builder]))

(defn ^AsyncLogger make-logger [{:keys [token debug? region]}]
  (AsyncLogger. (-> (LoggerConfiguration$Builder.)
                    (.useToken token)
                    (.useSSL true)
                    (.runInDebugMode (boolean debug?))
                    (.inRegion (or region "eu"))
                    (.build))))

(def iso-format "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

(def stack-trace-processor (map (fn [stack-frame]
                                  (if (:omitted stack-frame)
                                    (:formatted-name stack-frame)
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
             (catch Exception _
               (remove :omitted errors)))))))

(def illegal-key-characters #"[^a-zA-Z0-9@\$\._]")

(defn clean-key
  "InsightOps only supports alphanumeric values along with `@`, `$`, `_`, and `.` in keys. This function
  replaces all illegal characters in a string/keyword with a `.`. See https://docs.logentries.com/docs/json#section-kvp-parsing-specification for details."
  [k]
  (s/replace (name k) illegal-key-characters "_"))

(defn data->json-line
  "Create a JSON string to be sent to logentries, will clean keys of illegal characters by applying `clean-key`.

   N.B. Cheshire only applies `:key-fn` to keywords, not strings. So if a key is explicitly a string, then it will be passed through as is.
   While we could use clojure.walk to clean up all keys, it's an order of magnitude slower."
  [data user-tags stacktrace-fn]
  ;; Note: this it meant to target the logstash-filter-json; especially "message" and "@timestamp" get a special meaning there.
  (cheshire/generate-string
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
    {:date-format iso-format
     :pretty      false
     :key-fn      clean-key}))

(defn logentries-appender
  "Returns a Logentries appender, which will send each event in JSON format to the
  logentries server.  If you wish to send additional, custom tags, to logentries on each
  logging event, then provide a hash-map in the opts `:user-tags` which will be
  merged into each event.

  This uses com.logentries.net.AsyncLogger, which is the underlying implementation of the log4j and logback appenders from Logentries. That class uses a bounded queue and may drop messages under heavy load. When that happens, it will write error messages to stderr, but only if `:debug?` is `true`. See https://github.com/rapid7/le_java/blob/master/src/main/java/com/logentries/net/AsyncLogger.java.

  If an exception happens during logging, this appender will catch and not rethrow, meeting the standard expectation of a logging library. Exceptions will be logged to stderr at a rate of no more of 1 per minutes per appender. Additional information on the frequency of exxceptions may be found my inspecting the appender (see the fields `:call-count` and `:error-count`.

  Note that `cheshire.core` is used to serialize log messages to json. If something in your `:user-tags` or `:context` is not readily serializable by `cheshire`, this will cause exceptions and those messages *will not* be logged. See https://github.com/dakrone/cheshire#custom-encoders for how to teach `chechire` to encode your custom data."
  [token & [opts]]
  (let [stacktrace-fn   (:stack-trace-fn opts error-to-stacktrace)
        debug?          (:debug? opts false)]
    (let [logger                      (make-logger {:token token :debug? debug?})
          last-error-report-timestamp (atom 0)
          error-count                 (atom 0)
          call-count                  (atom 0)]
      {:enabled?                    true
       :async?                      false
       :min-level                   nil
       :rate-limit                  nil
       :output-fn                   :inherit
       :logger                      logger
       :last-error-report-timestamp last-error-report-timestamp
       :error-count                 error-count
       :call-count                  call-count
       :fn
       (fn [data]
         (try
           (swap! call-count inc)
           (let [line (data->json-line data (:user-tags opts) stacktrace-fn)]
             (.addLineToQueue logger line))
           (catch Exception e
             (swap! error-count inc)
             (try
               (let [now  (System/currentTimeMillis)
                     then @last-error-report-timestamp]
                 (when (> (- now then) (* 1000 60))
                   (when (compare-and-set! last-error-report-timestamp then now)
                     (binding [*out* *err*]
                       (printf "ERROR sending data to Logentries: %s\n" e)
                       (.printStackTrace e)))))
               (catch Exception _ nil)))))})))
