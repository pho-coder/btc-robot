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
(def ^:dynamic *buy-price* (atom nil))
(def ^:dynamic *chips* (atom {:money 5000 :btc 0}))
;; ({:action "buy" :price 12 :volume 1})
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
  [buy-price last-price]
  (if (not= @*buy-status* "HOLDING")
    (do (log/error "buy status:" @*buy-status* "can't buy now")
        false)
    (let [diff-rate (int (* 100 (/ (- buy-price last-price) last-price)))]
      (if (> diff-rate BUY-TOP-RATE)
        (do (log/error "buy price is too high!" diff-rate "more than" BUY-TOP-RATE)
            false)
        true))))



(defn down-stop?
  "down to stop point"
  []
  (let [staticmarket (utils/get-staticmarket)
        last-price (:last (:ticker staticmarket))
        diff-price (- @*buy-price* last-price)
        diff-rate (/ diff-price @*buy-price*)]
    (if (> diff-rate 0.15)
      (do (log/info "touch down stop point")
          true)
      false)))

(defn sell
  "sell now"
  []
  (let [staticmarket (utils/get-staticmarket)
        last-price (:last (:ticker staticmarket))]
    (log/info staticmarket)
    (log/info (str "sell at:" last-price))
    (reset! *buy-status* "HOLDING")))

(defn buy
  "buy now"
  []
  (let [staticmarket (utils/get-staticmarket)
        last-price (:last (:ticker staticmarket))
        open-price (:open (:ticker staticmarket))]
    (log/info staticmarket)
    (if (> last-price open-price)
      (do (log/info (str "buy at:" last-price))
          (reset! *buy-status* "BUYING")
          (reset! *buy-price* last-price))
      (log/info "buy fail"))))

(defn buy-or-sell
  "buy or sell"
  []
  (let [h-trend (utils/history-trend)]
    (log/info h-trend)
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
  (log/info "Hello, World!")
  (let [access_key (first args)
        secret_key (second args)
        kline-timer (timer/mk-timer)]
    (while true
      (update-kline-status)
      (log/info "kline status:" @*kline-status*)
      (buy-or-sell)
      (Thread/sleep 60000))))

