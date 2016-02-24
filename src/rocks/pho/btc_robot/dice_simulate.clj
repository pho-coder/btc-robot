(ns rocks.pho.btc-robot.dice-simulate
  (:require [clojure.data.json :as json]
            [rocks.pho.btc-robot.utils :as utils]))

(def ^:dynamic *buy-status* (atom "HOLDING"))
(def ^:dynamic *chips* (atom {:money 500000 :btc 0 :net-asset 500000}))
(def ^:dynamic *dice-results* (atom (list)))
(def ^:dynamic *actions* (atom (list)))

(defn write-kline-local
  [file-name]
  (let [a-kline (map utils/parse-kline-data (utils/get-kline "001"))]
    (spit file-name (json/write-str a-kline))))

(defn read-kline-local
  [file-name]
  (json/read-str (slurp file-name)
                 :key-fn keyword))

(defn dice-result!
  ""
  []
  (let [actions-now @*actions*
        dice-results @*dice-results*
        actions-size (.size actions-now)
        dice-results-size (.size dice-results)
        diff-size (- actions-size (* 2 dice-results-size))]
    (if (>= diff-size 2)
      (let [waitings (reverse (if (odd? diff-size)
                                (subvec (vec actions-now) 1 diff-size)
                                (subvec (vec actions-now) 0 diff-size)))]
        (loop [w waitings
               tmp {}]
          (if-not (empty? w)
            (let [one (first w)
                  action (:action one)
                  datetime (:datetime one)
                  net-asset (:net-asset one)]
              (case action
                "buy" (recur (pop w) {:buy-time datetime
                                      :net-asset net-asset})
                "sell" (let [result (- net-asset (:net-asset tmp))
                             right? (if (pos? result)
                                      true
                                      false)]
                         (swap! *dice-results* conj {:right? right?
                                                     :result result
                                                     :buy-time (:buy-time tmp)
                                                     :sell-time datetime})
                         (recur (pop w) {}))))))))))

(defn buy-simulate
  [buy-price now-one]
  (let [low-price (:low-price now-one)
        top-price (:top-price now-one)
        last-price (+ low-price (rand-int (inc (- top-price low-price))))
        diff-rate (int (* 10000 (double (/ (- last-price buy-price) buy-price))))]
    (if (> diff-rate 30)
      (prn "last price is too higher than 30 NO BUY")
      (if (< diff-rate -20)
        (prn "last price is too lower than -20 NO BUY")
        (let [money (:money @*chips*)
              buy-result {:result "success" :id "t1"}
              result (:result buy-result)
              id (:id buy-result)]
          (if (= result "success")
            (do (reset! *buy-status* "BUYING")
                (reset! *actions* (conj @*actions* {:action "buy"
                                                    :amount money
                                                    :id id
                                                    :datetime (:datetime now-one)
                                                    :net-asset money}))
                (reset! *chips* {:money 0
                                 :btc (double (/ (* money 100) last-price))
                                 :net-asset money})
                (prn "buy at:" last-price)
                (prn @*chips*))))))))

(defn sell-simulate
  [now-one]
  (let [low-price (:low-price now-one)
        top-price (:top-price now-one)
        last-price (+ low-price (rand-int (inc (- top-price low-price))))
        sell-result {:result "success" :id "t2"}
        result (:result sell-result)
        id (:id sell-result)
        money (* (:btc @*chips*) (double (/ last-price 100)))]
    (if (= result "success")
      (do (reset! *buy-status* "HOLDING")
          (reset! *actions* (conj @*actions* {:action "sell"
                                              :amount (:btc @*chips*)
                                              :id id
                                              :datetime (:datetime now-one)
                                              :net-asset money}))
          (reset! *chips* {:money money
                           :btc 0
                           :net-asset money})
          (prn "sell at:" last-price)
          (prn @*chips*)))))

(defn diff-kline-staticmarket
  []
  (let [s-last-price (int (* (:last (:ticker (utils/get-staticmarket))) 100))
        k-last-price (:end-price (last (utils/get-001-kline)))
        diff (- s-last-price k-last-price)]
    diff))

(defn simulate
  []
  (let [a-kline (map utils/parse-kline-data (utils/get-kline "001"))
        ;;a-kline (read-kline-local "/tmp/a")
        ]
    (loop [index 2]
      (if (<= index (.size a-kline))
        (let [status @*buy-status*
              k (subvec (vec a-kline) 0 index)
              kline (butlast k)
              now-one (last k)
              last-one (last kline)
              last-end-price (:end-price last-one)
              trend? (utils/trend-now? kline)]
          (if (= status "HOLDING")
            (if (= (:trend trend?) "up")
              (if (= "bet" (utils/dice-once (:kline trend?) "up"))
                (buy-simulate last-end-price now-one))))
          (if (= status "BUYING")
            (if (= (:trend trend?) "down")
              (if (= "bet" (utils/dice-once (:kline trend?) "down"))
                (sell-simulate now-one))))
          (recur (inc index))))))
  (dice-result!)
  (prn @*chips*)
  (map prn @*dice-results*))
