(ns youtube-looper.data-test
  (:require [cljs.test :refer-macros [is are deftest run-tests async]]
            [youtube-looper.data :as d]))

(deftest test-normalize-new-loop
  (is (= (d/normalize-new-loop {:loop/start "10" :loop/finish "20"})
         {:loop/start 10, :loop/finish 20, :loop/label "unnamed_section"})))

(deftest test-settings
  (let [conn (d/create-conn)]
    (is (= (into {} (d/settings @conn))
           {:ready? false, :show-dialog? false}))))

(deftest test-update-settings!
  (let [conn (d/create-conn)]
    (d/update-settings! conn {:current-video 10})
    (is (= (:current-video (d/settings @conn))
           10))))

(deftest test-create-from-new-loop!
  (let [conn (d/create-conn)]
    (d/set-current-video! conn 5)
    (d/update-new-loop! conn {:loop/start 10 :loop/finish 50})
    (d/create-from-new-loop! conn)
    (let [[loop] (d/loops-for-current-video @conn)]
      (is (= (into {} loop)
             {:loop/start 10
              :loop/finish 50
              :loop/label "unnamed_section"
              :loop/video 5})))))
