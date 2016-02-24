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
(def ^:dynamic *chips* (atom {:money 500000 :btc 0 :net-asset 500000}))
;;({:right? true :result 100 :buy-time :sell-time } {:right? false :result -100})
(def ^:dynamic *dice-results* (atom (list)))
;; ({:action "buy" :price 12 :volume 1 :type "up" :datetime 2016-01-01 14:20})
(def ^:dynamic *actions* (atom (list)))
;; {:price :datetime}
(def ^:dynamic *last-top-price* (atom nil))
(def ^:dynamic *last-low-price* (atom nil))

(def ^:dynamic *access-key* (atom nil))
(def ^:dynamic *secret-key* (atom nil))

(defn dice-result!
  ""
  []
  (let [actions-now @*actions*
        dice-results @*dice-results*
        actions-size (.size actions-now)
        dice-results-size (.size dice-results)
        diff-size (- actions-size (* 2 dice-results-size))]
    (if (>= diff-size 2)
      (let [waitings (reverse (if (odd? diff-size)
                                (subvec (vec actions-now) 1 diff-size)
                                (subvec (vec actions-now) 0 diff-size)))]
        (loop [w waitings
               tmp {}]
          (if-not (empty? w)
            (let [one (first w)
                  action (:action one)
                  datetime (:datetime one)
                  net-asset (:net-asset one)]
              (case action
                "buy" (recur (pop w) {:buy-time datetime
                                      :net-asset net-asset})
                "sell" (let [result (- net-asset (:net-asset tmp))
                             right? (if (pos? result)
                                      true
                                      false)]
                         (swap! *dice-results* conj {:right? right?
                                                     :result result
                                                     :buy-time (:buy-time tmp)
                                                     :sell-time datetime})
                         (recur (pop w) {}))))))))))

(defn update-kline-status
  "update kline status"
  []
  (reset! *kline-status* (utils/get-001-kline)))

(defn reset-new-account-info!
  "reset chips from new account info"
  []
  (let [account-info (utils/get-account-info @*access-key* @*secret-key*)
        available-cny-display (int (* 100 (Double/parseDouble (:available_cny_display account-info))))
        available-btc-display (Double/parseDouble (:available_btc_display account-info))
        net-asset (int (* 100 (Double/parseDouble (:net_asset account-info))))]
    (log/info account-info)
    (reset! *chips* {:money available-cny-display
                     :btc available-btc-display
                     :net-asset net-asset})))

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
      (let [account-info (utils/get-account-info @*access-key* @*secret-key*)
            available-cny-display (int (* 100 (Double/parseDouble (:available_cny_display account-info))))
            available-btc-display (Double/parseDouble (:available_btc_display account-info))
            net-asset (int (* 100 (Double/parseDouble (:net_asset account-info))))]
        (reset! *buy-status* "HOLDING")
        (reset! *actions* (conj @*actions* {:action "sell"
                                            :amount (:btc @*chips*)
                                            :id id
                                            :datetime (utils/now)
                                            :net-asset net-asset}))
        (reset! *chips* {:money available-cny-display
                         :btc available-btc-display
                         :net-asset net-asset})
          (log/info "sell at:" last-price))
      (do (log/error "sell market error!")
          (if (not= "1" (str (:code sell-result)))
            (System/exit 1))))))

(defn buy
  "buy now"
  [buy-price]
  (let [staticmarket (utils/get-staticmarket)
        last-price (int (* (:last (:ticker staticmarket)) 100))
        diff-rate (int (* 10000 (/ (- last-price buy-price) buy-price)))]
    (if (> diff-rate 30)
      (log/info "last price is too higher than 30 NO BUY")
      (if (< diff-rate -20)
        (log/info "last prcie is too lower than -20 NO BUY")
        (let [money (int (/ (:money @*chips*) 100))
              buy-result (utils/buy-market @*access-key* @*secret-key* money)
              _ (log/info buy-result)
              result (:result buy-result)
              id (:id buy-result)]
          (if (= result "success")
            (let [account-info (utils/get-account-info @*access-key* @*secret-key*)
                  available-cny-display (int (* 100 (Double/parseDouble (:available_cny_display account-info))))
                  available-btc-display (Double/parseDouble (:available_btc_display account-info))
                  net-asset (int (* 100 (Double/parseDouble (:net_asset account-info))))]
              (reset! *buy-status* "BUYING")
              (reset! *actions* (conj @*actions* {:action "buy"
                                                  :amount money
                                                  :id id
                                                  :datetime (utils/now)
                                                  :net-asset net-asset}))
              (reset! *chips* {:money available-cny-display
                               :btc available-btc-display
                               :net-asset net-asset})
              (log/info "buy at:" last-price))
            (do (log/error "buy market error!")
                (if (not= "1" (str (:code buy-result)))
                  (System/exit 1)))))))))

(defn watching
  "watch data, dice trend and bet it"
  []
  (let [status @*buy-status*
        kline (update-kline-status)
        now-one (last kline)
        start-price (:start-price now-one)
        end-price (:end-price now-one)
        top-price (:top-price now-one)
        low-price (:low-price now-one)
        volume (:volume now-one)
        end-diff-price (:end-diff-price now-one)
        max-diff-price (:max-diff-price now-one)
        trend? (utils/trend-now? (butlast kline))]
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
        (if (= "bet" (utils/dice-once (:kline trend?) "up" now-one))
          (buy last-end-price))))
    (if (= status "BUYING")
      (if (= (:trend trend?) "down")
        (if (= "bet" (utils/dice-once (:kline trend?) "down" now-one))
          (sell))))))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (log/info "Hello, World!")
  (let [access-key (first args)
        secret-key (second args)
        kline-timer (timer/mk-timer)
        watching-timer (timer/mk-timer)
        dice-result-timer (timer/mk-timer)]
    (reset! *access-key* access-key)
    (reset! *secret-key* secret-key)
    (reset-new-account-info!)
    (timer/schedule-recurring watching-timer 0 10
                              watching)
    (timer/schedule-recurring dice-result-timer 30 60
                              dice-result!)
    (Thread/sleep 5000)
    (while true
      (log/info "last top price:" @*last-top-price*)
      (log/info "last low price:" @*last-low-price*)
      (log/info "chips:" @*chips*)
      (log/info "actions:" @*actions*)
      (log/info "buy-status:" @*buy-status*)
      (log/info "dice result:" @*dice-results*)
      (Thread/sleep 60000))))
