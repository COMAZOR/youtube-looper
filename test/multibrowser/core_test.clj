(ns multibrowser.core-test
  (:require [clojure.test :refer :all]
            [multibrowser.core :refer :all]))

(deftest test-locale-files
  (is (= 2 (->> (locale-files)
                (count)))))

(deftest test-expand-language
  (is (= {:raw      "en-US"
          :country  "US"
          :language "en"}
         (expand-language "en-US"))))

(deftest test-read-locale
  (is (= {:language     {:raw      "en-US"
                         :country  "US"
                         :language "en"}
          :translations {:disable_loop    "Disable loop"
                         :new_loop        "Create a new loop"
                         :new_loop_name   "New loop name"
                         :unnamed_section "Unnamed section"}}
         (read-locale (first (locale-files))))))

(deftest test-chrome-locale->path
  (is (= "en" (chrome-locale->path {:country "US" :language "en"})))
  (is (= "pt_BR" (chrome-locale->path {:country "BR" :language "pt"})))
  (is (= "en_UK" (chrome-locale->path {:country "UK" :language "en"}))))

(deftest test-chrome-locale
  (is (= [{:target  "browsers/chrome/_locales/en/messages.json"
           :content "{\"second\":{\"message\":\"Two\"},\"first\":{\"message\":\"One\"}}"}]
         (chrome-locale {:language     {:raw      "en-US"
                                        :country  "US"
                                        :language "en"}
                         :translations {:first  "One" :second "Two"}}))))

(deftest test-firefox-locale
  (is (= [{:target  "browsers/firefox/locale/en-US.properties"
           :content "second= Two\nfirst= One"}
          {:target  "browsers/firefox/lib/l10n-keys.js"
           :content "module.exports = [\"second\",\"first\"];"}]
         (firefox-locale {:language     {:raw      "en-US"
                                         :country  "US"
                                         :language "en"}
                          :translations {:first  "One" :second "Two"}}))))

(deftest test-safari-locale
  (is (= [{:target  "browsers/safari/youtube-looper.safariextension/locale/en-US.edn"
           :content "{:second \"Two\", :first \"One\"}"}]
         (safari-locale {:language     {:raw      "en-US"
                                         :country  "US"
                                         :language "en"}
                          :translations {:first  "One" :second "Two"}}))))

(deftest test-translate
  (is (= [{:content "{\"second\":{\"message\":\"Two\"},\"first\":{\"message\":\"One\"}}"
           :target  "browsers/chrome/_locales/en/messages.json"}]
         (translate {:browser      :chrome
                     :translations {:first  "One" :second "Two"},
                     :language     {:raw "en-US" :language "en" :country "US"}}))))

(deftest test-expand-translations
  (is (= [{:content "{\"second\":{\"message\":\"Two\"},\"first\":{\"message\":\"One\"}}"
           :target  "browsers/chrome/_locales/en/messages.json"}
          {:content "second= Two\nfirst= One"
           :target  "browsers/firefox/locale/en-US.properties"}
          {:content "module.exports = [\"second\",\"first\"];"
           :target  "browsers/firefox/lib/l10n-keys.js"}
          {:content "{:second \"Two\", :first \"One\"}"
           :target  "browsers/safari/youtube-looper.safariextension/locale/en-US.edn"}]
         (expand-translations [{:translations {:first  "One" :second "Two"}
                                :language     {:raw "en-US" :language "en" :country "US"}}])))

  (testing "ensure output file list is distinct"
    (binding [*supported-browsers* [:firefox]]
      (is (= [{:content "first= One"
               :target  "browsers/firefox/locale/en-US.properties"}
              {:content "module.exports = [\"first\"];"
               :target  "browsers/firefox/lib/l10n-keys.js"}
              {:content "first= One"
               :target  "browsers/firefox/locale/pt-BR.properties"}]
             (expand-translations [{:translations {:first "One"}
                                    :language     {:raw "en-US" :language "en" :country "US"}}
                                   {:translations {:first "One"}
                                    :language     {:raw "pt-BR" :language "pt" :country "BR"}}]))))))
