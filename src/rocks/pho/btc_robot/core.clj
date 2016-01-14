(ns rocks.pho.btc-robot.core
  (:gen-class)

  (:require [clj-http.client :as client]
            [clojure.data.json :as json]))

(defn deal-one-kline
  "add other value for one kline"
  [data]
  (assoc data
         :up (if (> (:end-price data) (:start-price data))
                    true
                    false)
         ))

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

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))
