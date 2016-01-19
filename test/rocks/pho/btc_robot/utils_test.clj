(ns rocks.pho.btc-robot.utils-test
  (:require [clojure.test :refer :all]
            [rocks.pho.btc-robot.utils :refer :all]))

(deftest get-staticmarket-test
  (testing "get staticmarket"
    (prn (get-staticmarket))))
