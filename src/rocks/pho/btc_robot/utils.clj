(ns rocks.pho.btc-robot.utils
  (:require [clj-http.client :as client]
            [digest :as digest]
            [clojure.data.json :as json]
            [taoensso.timbre :as log]))

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
    (log/info kline)
    (if-not (= last-one second-last-one)
      "other"
      (case last-one
        "flat" "flat"
        "up" "up"
        "down" "down"))))
