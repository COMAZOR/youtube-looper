(ns youtube-looper.util-test
  (:require-macros [cemerick.cljs.test
                    :refer (is deftest with-test testing test-var)])
  (:require [cemerick.cljs.test :as t]
            [youtube-looper.util :as u]))

(deftest test-seconds->time
  (is (= (u/seconds->time 30) "00:30"))
  (is (= (u/seconds->time 65) "01:05"))
  (is (= (u/seconds->time 81.12) "01:21")))

(deftest test-time->seconds
  (is (= (u/time->seconds "00:30") 30))
  (is (= (u/time->seconds "01:05") 65))
  (is (= (u/time->seconds "01:21.12") 81.12)))

(deftest parse-time
  (is (= (u/parse-time "10") 10))
  (is (= (u/parse-time 50) 50))
  (is (= (u/parse-time "90.10") 90.10))
  (is (= (u/parse-time "1:30") 90))
  (is (= (u/parse-time "1:30.10") 90.10)))

(t/test-ns 'youtube-looper.util-test)
