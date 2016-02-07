(ns rocks.pho.btc-robot.utils-test
  (:require [clojure.test :refer :all]
            [rocks.pho.btc-robot.utils :refer :all]))

(deftest trend-now?-test
  (let [trend-up (list {:trend "up"} {:trend "down"} {:trend "up"} {:trend "up"} {:trend "up"})
        trend-others (list {:trend "down"} {:trend "down"} {:trend "up"} {:trend "down"})]
    (testing "trend-now? up test"
      (is (= {:trend "up"
              :kline (subvec (vec trend-up) 2)}
             (trend-now? trend-up)))
      (is (= {:trend "others"}
             (trend-now? trend-others))))))
