(ns rocks.pho.btc-robot.utils
  (:require [clj-http.client :as client]
            [digest :as digest]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [clj-time.local :as local-time]
            [clj-time.format :as format-time]))

(defn now
  "get now yyyy-MM-dd HH:mm:ss"
  []
  (let [n (local-time/local-now)
        f (format-time/formatter-local "yyyy-MM-dd HH:mm:ss")]
    (format-time/unparse f n)))

(defn get-account-info
  "get account info"
  [access_key secret_key]
  (let [unix-time (int (/ (System/currentTimeMillis) 1000))
        sign-str (str "access_key="
                      access_key
                      "&created="
                      unix-time
                      "&method=get_account_info&secret_key="
                      secret_key)
        sign (digest/md5 sign-str)]
    (json/read-str (:body (client/post "https://api.huobi.com/apiv3"
                                       {:headers {"Content-Type" "application/x-www-form-urlencoded"}
                                        :form-params {:method "get_account_info"
                                                      :access_key access_key
                                                      :created unix-time
                                                      :sign sign}})))))

(defn get-staticmarket
  "get realtime market"
  []
  (let [api-url "http://api.huobi.com/staticmarket/ticker_btc_json.js"]
    (json/read-str (:body (client/post api-url))
                   :key-fn keyword)))

(defn get-core-kline
  "get core data :datetime :up :end-price :diff-price :price-rate"
  [a-kline]
  (let [tmp-price (atom 0)]
    (map (fn [one]
           (let [datetime (:datetime one)
                 end-price (:end-price one)
                 diff-price (if (zero? @tmp-price)
                              0
                              (int (* 100 (- end-price @tmp-price))))]
             {:datetime datetime
              :trend (cond
                       (> diff-price 100) "up"
                       (< diff-price -100) "down"
                       :else "flat")
              :end-price end-price
              :diff-price diff-price
              :price-rate (if (= @tmp-price 0)
                            (do (reset! tmp-price end-price)
                                0)
                            (let [re (int (* (/ diff-price
                                                @tmp-price)
                                             10000))]
                              (reset! tmp-price end-price)
                              re))})) a-kline)))

(defn dice
  "predict the trend"
  [a-kline]
  (let [a-indexed-kline (map-indexed vector a-kline)
        predict-trend (fn [a-part-kline]
                        )]
    ))

(defn parse-kline-data
  "parse kline data from array to map"
  [data]
  (let [datetime (nth data 0)
        start-price (nth data 1)
        top-price (nth data 2)
        low-price (nth data 3)
        end-price (nth data 4)
        volume (nth data 5)
        end-diff-price (- end-price start-price)
        max-diff-price (- top-price low-price)]
    {:datetime (str (.substring datetime 0 4)
                    "-"
                    (.substring datetime 4 6)
                    "-"
                    (.substring datetime 6 8)
                    " "
                    (.substring datetime 8 10)
                    ":"
                    (.substring datetime 10 12))
     :start-price start-price
     :top-price top-price
     :low-price low-price
     :end-price end-price
     :volume volume
     :end-diff-price end-diff-price
     :end-diff-price-rate (int (* 100 (/ end-diff-price start-price)))
     :max-diff-price max-diff-price}))

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
  (reverse (map parse-kline-data (take last-n (reverse (get-kline type))))))

(defn history-trend
  "judge trend by last two kline"
  []
  (let [kline (reverse (get-core-kline (get-last-kline "001" 10)))
        last-one (first kline)
        last-one-trend (:trend last-one)
        second-last-one-trend (:trend (second kline))]
    (log/debug kline)
    {:datetime (:datetime last-one)
     :end-price (:end-price last-one)
     :trend (if-not (= last-one-trend second-last-one-trend)
              "other"
              (case last-one-trend
                "flat" "flat"
                "up" "up"
                "down" "down"))}))
