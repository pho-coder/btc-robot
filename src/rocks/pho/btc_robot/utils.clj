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
                                                      :sign sign}}))
                   :key-fn keyword)))

(defn buy-market
  "buy now"
  [access-key secret-key amount]
  (let [unix-time (int (/ (System/currentTimeMillis) 1000))
        sign-str (str "access_key=" access-key
                      "&amount=" amount
                      "&coin_type=1"
                      "&created=" unix-time
                      "&method=buy_market"
                      "&secret_key=" secret-key)
        sign (digest/md5 sign-str)]
    (json/read-str (:body (client/post "https://api.huobi.com/apiv3"
                                       {:headers {"Content-Type" "application/x-www-form-urlencoded"}
                                        :form-params {:method "buy_market"
                                                      :access_key access-key
                                                      :coin_type 1
                                                      :amount amount
                                                      :created unix-time
                                                      :sign sign}}))
                   :key-fn keyword)))

(defn sell-market
  "sell now"
  [access-key secret-key amount]
  (let [unix-time (int (/ (System/currentTimeMillis) 1000))
        sign-str (str "access_key=" access-key
                      "&amount=" amount
                      "&coin_type=1"
                      "&created=" unix-time
                      "&method=sell_market"
                      "&secret_key=" secret-key)
        sign (digest/md5 sign-str)]
    (json/read-str (:body (client/post "https://api.huobi.com/apiv3"
                                       {:headers {"Content-Type" "application/x-www-form-urlencoded"}
                                        :form-params {:method "sell_market"
                                                      :access_key access-key
                                                      :coin_type 1
                                                      :amount amount
                                                      :created unix-time
                                                      :sign sign}}))
                   :key-fn keyword)))

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

(defn dice-once
  "predict trend once"
  [a-kline up-down?]
  (let [start-price (:start-price (first a-kline))
        end-price (:end-price (last a-kline))
        diff-rate (int (/ (* 10000 (- end-price start-price)) start-price))]
    (case up-down?
      "up" (when (> diff-rate 10) (log/info "buy point at:" (last a-kline)) "bet")
      "down" (when (< diff-rate -10) (log/info "sell point at:" (last a-kline)) "bet"))))

(defn trend-now?
  "judge the last data whether is trending. at least three times up or down. get a time-sorted list return a time-sorted list"
  [a-kline]
  (let [reverse-kline (reverse a-kline)
        trend (:trend (first reverse-kline))
        cuted-kline (loop [before-kline reverse-kline
                          after-kline (list)]
                     (if (empty? before-kline)
                       after-kline
                       (let [last-one (first before-kline)]
                         (if (= (:trend last-one)
                                trend)
                           (recur (pop before-kline) (conj after-kline last-one))
                           (recur (list) after-kline)))))]
    (if (>= (.size cuted-kline)
            2)
      {:trend trend
       :kline cuted-kline}
      {:trend "others"
       :kline cuted-kline})))

(defn parse-kline-data
  "parse kline data from array to map"
  [data]
  (let [datetime (nth data 0)
        start-price (int (* 100 (nth data 1)))
        top-price (int (* 100 (nth data 2)))
        low-price (int (* 100 (nth data 3)))
        end-price (int (* 100 (nth data 4)))
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
     :end-diff-price-rate (int (* 10000 (/ end-diff-price start-price)))
     :max-diff-price max-diff-price
     :max-diff-price-rate (int (* 10000 (/ max-diff-price low-price)))
     :trend (cond
              (< end-diff-price 0) "down"
              (> end-diff-price 0) "up"
              (= end-diff-price 0) "flat"
              :else (throw (Exception. "end-diff-price error!")))}))

(defn get-kline
  "get kline 001 005 ..."
  [type]
  (let [url (case type
              "001" (str "http://api.huobi.com/staticmarket/btc_kline_" type "_json.js")
              (throw (Exception. "kline type error: " type)))]
    (json/read-str (:body (client/get url)))))

(defn get-last-kline
  "get last n kline by type"
  [type last-n]
  (reverse (map parse-kline-data (take last-n (reverse (get-kline type))))))
