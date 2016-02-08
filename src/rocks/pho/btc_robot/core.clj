(ns rocks.pho.btc-robot.core
  (:gen-class)

  (:require [clj-http.client :as client]
            [clj-time.core :as t]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [rocks.pho.btc-robot.utils :as utils]
            [com.jd.bdp.magpie.util.timer :as timer]))

;; {:datetime :trend :end-price}
(def ^:dynamic *kline-status* (atom {}))
;;HOLDING BUYING
(def ^:dynamic *buy-status* (atom "HOLDING"))
(def ^:dynamic *chips* (atom {:money 5000 :btc 0}))
;; ({:action "buy" :price 12 :volume 1 :type "up" :datetime 2016-01-01 14:20})
(def ^:dynamic *actions* (atom (list)))

(def BUY-TOP-RATE 15)
(def TRANSACTION-TYPE-UP "up")
(def TRANSACTION-TYPE-DOWN "down")
(def TRANSACTION-TYPE-FORCE "force")

(defn update-kline-status
  "update kline status"
  []
  (reset! *kline-status* (map utils/parse-kline-data (utils/get-kline "001"))))

(defn can-buy?
  "if can buy"
  [buy-price]
  (if (not= @*buy-status* "HOLDING")
    {:re false}
    (let [last-end-price (:end-price @*kline-status*)
          diff-rate (int (* 100 (/ (- buy-price last-end-price) last-end-price)))]
      (if (> diff-rate BUY-TOP-RATE)
        (do (log/error "buy price is too high!" diff-rate "more than" BUY-TOP-RATE)
            {:re false})
        (if (= (:trend @*kline-status*) "up")
          {:re true
           :type TRANSACTION-TYPE-UP}
          {:re false})))))

(defn can-sell?
  "if can sell"
  [sell-price]
  (if (not= @*buy-status* "BUYING")
    false
    (let [trend (:trend @*kline-status*)]
      (if (= trend "up")
        {:re false}
        (let [buy-price (:price (first @*actions*))
              diff-rate (int (* 100 (/ (- buy-price sell-price) buy-price)))]
          (if (> diff-rate BUY-TOP-RATE)
            (do (log/info "price is too low, sell!")
                {:re true
                 :type TRANSACTION-TYPE-FORCE})
            (if (= trend "down")
              {:re true
               :type TRANSACTION-TYPE-DOWN}
              {:re false})))))))

(defn sell
  "sell now"
  []
  (let [staticmarket (utils/get-staticmarket)
        last-price (int (* (:last (:ticker staticmarket)) 100))]
    (reset! *buy-status* "HOLDING")
    (reset! *actions* (conj @*actions* {:action "sell"
                                        :price last-price
                                        :volume 1
                                        :datetime (utils/now)}))
    (reset! *chips* {:money (+ (:money @*chips*) last-price)
                     :btc 0})
    (log/info type "sell at:" last-price)))

(defn buy
  "buy now"
  [buy-price]
  (let [staticmarket (utils/get-staticmarket)
        last-price (int (* (:last (:ticker staticmarket)) 100))
        diff-rate (int (* 10000 (/ (- last-price buy-price) buy-price)))]
    (if (> diff-rate 50)
      (log/info "last price is too higher than 50 NO BUY")
      (if (< diff-rate -20)
        (log/info "last prcie is too lower than -20 NO BUY")
        (do (reset! *actions* (conj @*actions* {:action "buy"
                                                :price last-price
                                                :volume 1
                                                :datetime (utils/now)}))
            (reset! *chips* {:money (- (:money @*chips*) last-price)
                             :btc 1})
            (reset! *buy-status* "BUYING")
            (log/info "buy at:" last-price))))))

(defn buy-or-sell
  "buy or sell"
  []
  (let [staticmarket (utils/get-staticmarket)
        last-price (:last (:ticker staticmarket))
        can-buy (can-buy? last-price)
        can-sell (can-sell? last-price)]
    (case @*buy-status*
      "HOLDING" (if (:re can-buy)
                  (buy last-price (:type can-buy)))
      "BUYING" (if (:re can-sell)
                 (sell last-price (:type can-sell))))))

(defn watching
  "watch data, dice trend and bet it"
  []
  (let [status @*buy-status*
        kline @*kline-status*
        last-one (last kline)
        last-end-price (:end-price last-one)
        trend? (utils/trend-now? kline)]
    (if (= status "HOLDING")
      (if (= (:trend trend?) "up")
        (if (= "bet" (utils/dice-once (:kline trend?) "up"))
          (buy last-end-price))))
    (if (= status "BUYING")
      (if (= (:trend trend?) "down")
        (if (= "bet" (utils/dice-once (:kline trend?) "down"))
          (sell))))))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (log/info "Hello, World!")
  (let [access_key (first args)
        secret_key (second args)
        kline-timer (timer/mk-timer)
        watching-timer (timer/mk-timer)]
    (timer/schedule-recurring kline-timer 0 60
                              update-kline-status)
    (timer/schedule-recurring watching-timer 10 30
                              watching)
    (while true
      (Thread/sleep 60000)
      (log/info "kline status:" @*kline-status*)
      (log/info "chips:" @*chips*)
      (log/info "actions:" @*actions*)
      (log/info "buy-status:" @*buy-status*))))

