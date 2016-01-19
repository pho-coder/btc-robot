(ns rocks.pho.btc-robot.core
  (:gen-class)

  (:require [clj-http.client :as client]
            [clojure.data.json :as json]
            [digest :as digest]))

(defn get-core-kline
  "get core data :datetime :up :end-price :diff-price :price-rate"
  [a-kline]
  (let [tmp-price (atom 0)]
    (map (fn [one]
           (let [datetime (:datetime one)
                 up (:up one)
                 end-price (:end-price one)
                 diff-price (if (= @tmp-price 0)
                              0
                              (int (* 100 (- end-price @tmp-price))))]
             {:datetime datetime
              :up up
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

(defn deal-one-kline
  "add other value for one kline"
  [data]
  (assoc data
         :up (if (> (:end-price data) (:start-price data))
                    true
                    false)))

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
  (reverse (map #(deal-one-kline (parse-kline-data %)) (take last-n (reverse (get-kline type))))))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!")
  (let [access_key (first args)
        secret_key (second args)]
    (prn access_key secret_key)))
