(ns rocks.pho.btc-robot.core-test
  (:require [clojure.test :refer :all]
            [rocks.pho.btc-robot.core :refer :all]))

(deftest a-test
  (testing "get kline"
    (prn (get-core-kline (get-last-kline "001" 10)))
    (is (= 1 1))))

