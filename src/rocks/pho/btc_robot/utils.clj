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

(defn dice-once
  "predict trend once"
  [a-kline up-down?]
  (let [len (.size a-kline)
        start-price (:start-price (first a-kline))
        end-price (:end-price (last a-kline))
        diff-rate (int (/ (* 10000 (- end-price start-price)) start-price))]
    (case up-down?
      "up" (if (> diff-rate 20)
             (log/info "buy point at:" (last a-kline)))
      "down" (if (< diff-rate -10)
               (log/info "sell point at:" (last a-kline))))))

(defn dice-them
  "dice the trend by real data"
  [a-kline]
  (let [trend-step 3
        a-indexed-kline (map-indexed vector a-kline)
        start-point (atom nil) ;; {:trend "up" :index 12}
        trend-now (fn [end-diff-price]
                    (cond
                      (> end-diff-price 0) "up"
                      (< end-diff-price 0) "down"
                      (= end-diff-price 0) "flat"
                      :else "others"))
        reset-start-point! (fn [a-vec]
                             (let [[index kline] a-vec]
                               (if (= index 0)
                                 (reset! start-point {:index 0
                                                      :trend (trend-now (:end-diff-price kline))})
                                 (let [this-trend (trend-now (:end-diff-price kline))]
                                   (if (= this-trend (:trend @start-point))
                                     (if (and (>= (- (inc index) (:index @start-point)
                                                     trend-step))
                                              (or (= this-trend "up")
                                                  (= this-trend "down")))
                                       (dice-once (subvec (vec a-kline)
                                                          (:index @start-point)
                                                          (inc index))
                                                  this-trend))
                                     (reset! start-point {:index index
                                                          :trend this-trend}))))))]
    (map reset-start-point! a-indexed-kline)))

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
     :max-diff-price-rate (int (* 10000 (/ max-diff-price low-price)))}))

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
