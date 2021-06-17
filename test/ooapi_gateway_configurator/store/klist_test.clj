(ns ooapi-gateway-configurator.store.klist-test
  (:require [ooapi-gateway-configurator.store.klist :as klist]
            [clojure.test :refer [deftest is testing]]))

(deftest keylist
  (is (klist/klist? [{:bubbles 1}
                     {:barney 2}
                     {:fred 3}]))

  (is (not (klist/klist? {:bubbles 1})))

  (is (= 2
         (klist/get [{:bubbles 1}
                     {:barney 2}
                     {:fred 3}]
                    :barney))
      "get on klist")

  (is (= 2
         (klist/get {:bubbles 1
                     :barney  2
                     :fred    3}
                    :barney))
      "get on normal map")

  (is (= 2
         (klist/get-in {:rubbles     [{:barney 3}]
                        :flintstones [{:wilma 1}
                                      {:fred 2}]}
                       [:flintstones :fred]))
      "get-in on map with klist")

  (is (= [{:bubbles 1}
          {:barney :changed}
          {:fred 3}]
         (klist/assoc [{:bubbles 1}
                       {:barney 2}
                       {:fred 3}]
                      :barney :changed))
      "assoc on klist")

  (is (= {:bubbles 1
          :barney  :changed
          :fred    3}
         (klist/assoc {:bubbles 1
                       :barney  2
                       :fred    3}
                      :barney :changed))
      "assoc on normal map")

  (is (= {:rubbles     [{:barney 3}]
          :flintstones [{:wilma 1}
                        {:fred :changed}]}
         (klist/assoc-in {:rubbles     [{:barney 3}]
                          :flintstones [{:wilma 1}
                                        {:fred 2}]}
                         [:flintstones :fred] :changed))
      "assoc-in on map of klists")

  (is (= [{:bubbles 1}
          {:barney 20}
          {:fred 3}]
         (klist/update [{:bubbles 1}
                        {:barney 2}
                        {:fred 3}]
                       :barney * 10))
      "update on klist")

  (is (= {:bubbles 1
          :barney  20
          :fred    3}
         (klist/update {:bubbles 1
                        :barney  2
                        :fred    3}
                       :barney * 10))
      "update on normal map")


  (is (= {:rubbles     [{:barney 3}]
          :flintstones [{:wilma 1}
                        {:fred 20}]}
         (klist/update-in {:rubbles     [{:barney 3}]
                           :flintstones [{:wilma 1}
                                         {:fred 2}]}
                          [:flintstones :fred] * 10))
      "update-in on map of klists"))
