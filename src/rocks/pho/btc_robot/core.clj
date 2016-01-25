(ns rocks.pho.btc-robot.core
  (:gen-class)

  (:require [clj-http.client :as client]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [rocks.pho.btc-robot.utils :as utils]
            [com.jd.bdp.magpie.util.timer :as timer]))

;; {:datetime :trend :end-price}
(def ^:dynamic *kline-status* (atom {}))
(def ^:dynamic *buy-status* (atom "HOLDING"))
(def ^:dynamic *chips* (atom {:money 5000 :btc 0}))
;; ({:action "buy" :price 12 :volume 1 :datetime 2016-01-01 14:20})
(def ^:dynamic *actions* (atom (list)))

(def BUY-TOP-RATE 15)

(defn update-kline-status
  "update kline status"
  []
  (let [h-trend (utils/history-trend)]
    (reset! *kline-status* {:datetime (:datetime h-trend)
                            :trend (:trend h-trend)
                            :end-price (:end-price h-trend)})))

(defn can-buy?
  "if can buy"
  [buy-price]
  (if (not= @*buy-status* "HOLDING")
    (do (log/error "buy status:" @*buy-status* "can't buy now")
        false)
    (let [last-end-price (:end-price @*kline-status*)
          diff-rate (int (* 100 (/ (- buy-price last-end-price) last-end-price)))]
      (if (> diff-rate BUY-TOP-RATE)
        (do (log/error "buy price is too high!" diff-rate "more than" BUY-TOP-RATE)
            false)
        (if (= (:trend @*kline-status*) "up")
          true
          false)))))

(defn can-sell?
  "if can sell"
  [sell-price]
  (if (not= @*buy-status* "BUYING")
    false
    (let [trend (:trend @*kline-status*)]
      (if (= trend "up")
        false
        (let [buy-price (:price (first @*actions*))
              diff-rate (int (* 100 (/ (- buy-price sell-price) buy-price)))]
          (if (> diff-rate BUY-TOP-RATE)
            (do (log/info "price is too low, sell!")
                true)
            false))))))

(defn sell
  "sell now"
  [sell-price]
  (reset! *buy-status* "HOLDING")
  (reset! *actions* (conj @*actions* {:action "sell"
                                      :price sell-price
                                      :volume 1
                                      :datetime (System/currentTimeMillis)}))
  (reset! *chips* {:money (+ (:money @*chips*) sell-price)
                   :btc 0})
  (log/info "sell at:" sell-price))

(defn buy
  "buy now"
  [buy-price]
  (reset! *buy-status* "BUYING")
  (reset! *actions* (conj @*actions* {:action "buy"
                                      :price buy-price
                                      :volume 1
                                      :datetime (System/currentTimeMillis)}))
  (reset! *chips* {:money (- (:money @*chips*) buy-price)
                   :btc 1})
  (log/info "buy at:" buy-price))

(defn buy-or-sell
  "buy or sell"
  []
  (let [staticmarket (utils/get-staticmarket)
        last-price (:last (:ticker staticmarket))]
    (case @*buy-status*
      "HOLDING" (if (can-buy? last-price)
                  (buy last-price))
      "BUYING" (sell last-price))))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (log/info "Hello, World!")
  (let [access_key (first args)
        secret_key (second args)
        kline-timer (timer/mk-timer)
        transaction-timer (timer/mk-timer)]
    (timer/schedule-recurring kline-timer 0 60
                              (fn []
                                (update-kline-status)))
    (timer/schedule-recurring transaction-timer 10 30
                              (fn []
                                (buy-or-sell)))
    (while true
      (Thread/sleep 60000)
      (log/info "kline status:" @*kline-status*)
      (log/info "chips:" @*chips*)
      (log/info "actions:" @*actions*)
      (log/info "buy-status:" @*buy-status*))))

