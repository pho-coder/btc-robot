(ns rocks.pho.btc-robot.core
  (:gen-class)

  (:require [clj-http.client :as client]
            [clojure.data.json :as json]
            [rocks.pho.btc-robot.utils :as utils]))

(def ^:dynamic *buy-status* (atom "HOLDING"))
(def ^:dynamic *buy-price* (atom nil))

(defn get-core-kline
  "get core data :datetime :up :end-price :diff-price :price-rate"
  [a-kline]
  (let [tmp-price (atom 0)]
    (map (fn [one]
           (let [datetime (:datetime one)
                 end-price (:end-price one)
                 diff-price (if (= @tmp-price 0)
                              0
                              (int (* 100 (- end-price @tmp-price))))]
             {:datetime datetime
              :trend (cond
                       (> diff-price 100) "up"
                       (< diff-price -100) "down"
                       :else "flat")
              :end-prcie end-price
              :diff-price diff-price
              :price-rate (if (= @tmp-price 0)
                            (do (reset! tmp-price end-price)
                                0)
                            (let [re (int (* (/ diff-price
                                                @tmp-price)
                                             10000))]
                              (reset! tmp-price end-price)
                              re))})) a-kline)))

(defn parse-kline-data
  "parse kline data from array to map"
  [data]
  {:datetime (let [datetime (nth data 0)]
               (str (.substring datetime 0 4)
                    "-"
                    (.substring datetime 4 6)
                    "-"
                    (.substring datetime 6 8)
                    " "
                    (.substring datetime 8 10)
                    ":"
                    (.substring datetime 10 12)))
   :start-price (nth data 1)
   :top-price (nth data 2)
   :low-price (nth data 3)
   :end-price (nth data 4)
   :volume (nth data 5)})

(defn get-kline
  "get kline 001 005 ..."
  [type]
  (let [url (case type
              "001" (str "http://api.huobi.com/staticmarket/btc_kline_" type "_json.js")
              (throw Exception "kline type error: " type))]
    (json/read-str (:body (client/get url)))))

(defn get-last-kline
  "get last n kline by type"
  [type last-n]
  (reverse (map #(parse-kline-data %) (take last-n (reverse (get-kline type))))))

(defn history-trend
  "judge trend by last two kline"
  []
  (let [kline (reverse (get-core-kline (get-last-kline "001" 10)))
        last-one (:trend (first kline))
        second-last-one (:trend (second kline))]
    (prn kline)
    (if-not (= last-one second-last-one)
      "other"
      (case last-one
        "flat" "flat"
        "up" "up"
        "down" "down"))))

(defn down-stop?
  "down to stop point"
  []
  (let [staticmarket (utils/get-staticmarket)
        last-price (:last (:ticker staticmarket))
        diff-price (- @*buy-price* last-price)
        diff-rate (/ diff-price @*buy-price*)]
    (if (> diff-rate 0.15)
      (do (prn "touch down stop point")
          true)
      false)))

(defn sell
  "sell now"
  []
  (let [staticmarket (utils/get-staticmarket)
        last-price (:last (:ticker staticmarket))]
    (prn staticmarket)
    (prn (str "sell at:" last-price))
    (reset! *buy-status* "HOLDING")))

(defn buy
  "buy now"
  []
  (let [staticmarket (utils/get-staticmarket)
        last-price (:last (:ticker staticmarket))
        open-price (:open (:ticker staticmarket))]
    (prn staticmarket)
    (if (> last-price open-price)
      (do (prn (str "buy at:" last-price))
          (reset! *buy-status* "BUYING")
          (reset! *buy-price* last-price))
      (prn "buy fail"))))

(defn buy-or-sell
  "buy or sell"
  []
  (let [h-trend (history-trend)]
    (prn h-trend)
    (case @*buy-status*
      "BUYING" (if (down-stop?)
                 (do (sell)
                     (System/exit 1))
                 (if (= h-trend "down")
                   (sell)))
      "HOLDING" (if (= h-trend "up")
                  (buy)))))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!")
  (let [access_key (first args)
        secret_key (second args)]
    (while true
      (buy-or-sell)
      (Thread/sleep 10000))))
