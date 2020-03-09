(ns yummly.logentries-timbre-appender-test
  (:require [yummly.logentries-timbre-appender :as sut]
            [clojure.test :refer :all]
            [clojure.string]
            [cheshire.core :as cheshire]))

(deftest error-to-stacktrace-test
  (let [test-error   ((fn [] (java.lang.ArithmeticException. "Divide by fake-value")))
        parsed-error (first (sut/error-to-stacktrace test-error))]
    (is (= "java.lang.ArithmeticException" (:class-name parsed-error)))
    (is (= "Divide by fake-value" (:message parsed-error)))
    (is (pos? (count (:stack-trace parsed-error)))
        "At the very least, we should have a stack trace from the nested function call")
    (is (every? string? (:stack-trace parsed-error)))))

(deftest update-illegal-characters
  (let [test-line (cheshire/parse-string (sut/data->json-line {:msg_ {:test-data {:test-data             5
                                                                                  (keyword "casdf asdf") "dog"}}}
                                                              {:user-data :dog} identity))]
    (is (= (get test-line "message")
           {"test_data" {"test_data"  5
                         "casdf_asdf" "dog"}}))
    (is (= (get test-line "user_data")
           "dog"))))
