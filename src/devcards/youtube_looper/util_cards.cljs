(ns youtube-looper.util-cards
  (:require [cljs.test :refer-macros [is async testing]]
            [youtube-looper.util :as u])
  (:require-macros [devcards.core :as dc :refer [defcard deftest]]))

(deftest test-seconds->time
  (is (= (u/seconds->time 30) "00:30"))
  (is (= (u/seconds->time 60) "01:00"))
  (is (= (u/seconds->time 85) "01:25"))
  (is (= (u/seconds->time 85.345) "01:25"))
  (is (= (u/seconds->time 85.345 2) "01:25.34"))
  (is (= (u/seconds->time 65.345 3) "01:05.345")))
