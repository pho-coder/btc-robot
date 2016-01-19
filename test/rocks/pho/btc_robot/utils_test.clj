(ns rocks.pho.btc-robot.utils-test
  (:require [clojure.test :refer :all]
            [rocks.pho.btc-robot.utils :refer :all]))

(deftest get-account-info-test
  (testing "get account info"
    (let [access_key (first args)
          secret_key (second args)]
      (prn access_key secret_key))))
