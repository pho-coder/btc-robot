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
(def ^:dynamic *chips* (atom {:money 500000 :btc 0}))
;; ({:action "buy" :price 12 :volume 1 :type "up" :datetime 2016-01-01 14:20})
(def ^:dynamic *actions* (atom (list)))
;; {:price :datetime}
(def ^:dynamic *last-top-price* (atom nil))
(def ^:dynamic *last-low-price* (atom nil))

(def ^:dynamic *access-key* (atom nil))
(def ^:dynamic *secret-key* (atom nil))

(defn update-kline-status
  "update kline status"
  []
  (reset! *kline-status* (map utils/parse-kline-data (utils/get-kline "001"))))

(defn reset-new-account-info!
  "reset chips from new account info"
  []
  (let [account-info (utils/get-account-info @*access-key* @*secret-key*)
        available-cny-display (int (* 100 (Double/parseDouble (:available_cny_display account-info))))
        available-btc-display (Double/parseDouble (:available_btc_display account-info))]
    (log/info account-info)
    (reset! *chips* {:money available-cny-display
                     :btc available-btc-display})))

(defn sell
  "sell now"
  []
  (let [staticmarket (utils/get-staticmarket)
        last-price (int (* (:last (:ticker staticmarket)) 100))
        sell-result (utils/sell-market @*access-key* @*secret-key* (:btc @*chips*))
        _ (log/info sell-result)
        result (:result sell-result)
        id (:id sell-result)]
    (if (= result "success")
      (do (reset! *buy-status* "HOLDING")
          (reset! *actions* (conj @*actions* {:action "sell"
                                              :amount (:btc @*chips*)
                                              :id id
                                              :datetime (utils/now)}))
          (reset-new-account-info!)
          (log/info type "sell at:" last-price))
      (do (log/error "sell market error!")
          (if (not= "1" (str (:code sell-result)))
            (System/exit 1))))))

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
        (let [buy-result (utils/buy-market @*access-key* @*secret-key* 3000)
              _ (log/info buy-result)
              result (:result buy-result)
              id (:id buy-result)]
          (if (= result "success")
            (do (reset! *buy-status* "BUYING")
                (reset! *actions* (conj @*actions* {:action "buy"
                                                    :amount 3000
                                                    :id id
                                                    :datetime (utils/now)}))
                (reset-new-account-info!)
                (log/info "buy at:" last-price))
            (do (log/error "buy market error!")
                (if (not= "1" (str (:code buy-result)))
                  (System/exit 1)))))))))

(defn watching
  "watch data, dice trend and bet it"
  []
  (let [status @*buy-status*
        kline @*kline-status*
        last-one (last kline)
        last-end-price (:end-price last-one)
        trend? (utils/trend-now? kline)]
    (log/info trend?)
    (let [trend (:trend trend?)
          last-kline (last (:kline trend?))
          end-price (:end-price last-kline)
          datetime (:datetime last-kline)]
      (if (= "up" trend)
        (reset! *last-top-price* {:price end-price
                                  :datetime datetime}))
      (if (= "down" trend)
        (reset! *last-low-price* {:price end-price
                                  :datetime datetime})))
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
  (let [access-key (first args)
        secret-key (second args)
        kline-timer (timer/mk-timer)
        watching-timer (timer/mk-timer)]
    (reset! *access-key* access-key)
    (reset! *secret-key* secret-key)
    (reset-new-account-info!)
    (timer/schedule-recurring kline-timer 0 60
                              update-kline-status)
    (timer/schedule-recurring watching-timer 10 30
                              watching)
    (Thread/sleep 5000)
    (while true
      (log/info "last top price:" @*last-top-price*)
      (log/info "last low price:" @*last-low-price*)
      (log/info "chips:" @*chips*)
      (log/info "actions:" @*actions*)
      (log/info "buy-status:" @*buy-status*)
      (Thread/sleep 60000))))

