(ns rocks.pho.btc-robot.core
  (:gen-class)

  (:require [clj-http.client :as client]
            [clj-time.core :as t]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [rocks.pho.btc-robot.utils :as utils]
            [com.jd.bdp.magpie.util.timer :as timer]
            [mount.core :as mount]
            [clojure.tools.cli :refer [parse-opts]]

            [rocks.pho.btc-robot.config :refer [env]]))

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
                         (recur (pop w) {})))))))))
  (log/info "dice result:" @*dice-results*)
  (log/info "total:" (reduce + (map :result @*dice-results*))))

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
;;    (log/info account-info)
    (reset! *chips* {:money available-cny-display
                     :btc available-btc-display
                     :net-asset net-asset})))

(defn sell
  "sell now default retry times: 5"
  ([]
   (sell 5))
  ([retry-times]
   (when (pos? retry-times)
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
                                               :net-asset net-asset
                                               :price (/ net-asset (:btc @*chips*))}))
           (reset! *chips* {:money available-cny-display
                            :btc available-btc-display
                            :net-asset net-asset})
           (log/info "sell at:" last-price))
         (do (log/error "sell market error!")
             (if (= "1" (str (:code sell-result)))
               (do (Thread/sleep 500)
                   (sell (dec retry-times)))
               (System/exit 1))))))))

(defn buy
  "buy now default retry times: 5"
  ([now-one]
   (buy now-one 5))
  ([now-one retry-times]
   (when (pos? retry-times)
     (let [staticmarket (utils/get-staticmarket)
           last-price (int (* (:last (:ticker staticmarket)) 100))
           end-price (:end-price now-one)
           diff-price (- last-price end-price)
           diff-rate (int (* 10000 (/ diff-price end-price)))]
       (if (> diff-price 500)
         (log/info "last price is too higher than 5.00 NO BUY")
         (if (< diff-price -300)
           (log/info "last prcie is too lower than -3.00 NO BUY")
           (let [money (int (/ (:money @*chips*) 100))
                 buy-result (utils/buy-market @*access-key* @*secret-key* money)
                 _ (log/info buy-result)
                 _ (log/info "now one:" now-one)
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
                                                     :net-asset net-asset
                                                     :price (int (/ net-asset available-btc-display))}))
                 (reset! *chips* {:money available-cny-display
                                  :btc available-btc-display
                                  :net-asset net-asset})
                 (log/info "buy at:" last-price))
               (do (log/error "buy market error!")
                   (if (= "1" (str (:code buy-result)))
                     (do (Thread/sleep 500)
                         (buy now-one (dec retry-times)))
                     (System/exit 1)))))))))))

(defn watching
  "watch data, dice trend and bet it"
  []
  (log/info "I am watching!")
  (try
    (let [status @*buy-status*
          kline (update-kline-status)
          now-one (last kline)
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
      (when (= status "BUYING")
        (let [buy-price (:price (first @*actions*))
              diff-price (- (:end-price now-one) buy-price)]
          (log/info "now diff price:" diff-price)
          (when (< diff-price -500)
            (log/error "now price too low than buy price:" diff-price)
            (sell)))
        (if (= (:trend trend?) "down")
          (if (= "bet" (utils/dice-once (:kline trend?) "down" now-one))
            (sell))))
      (if (= status "HOLDING")
        (if (= (:trend trend?) "up")
          (if (= "bet" (utils/dice-once (:kline trend?) "up" now-one))
            (buy now-one))))
      (reset-new-account-info!))
    (catch Exception e
      (log/error e))))

(defn stop-app
  []
  (doseq [component (:stopped (mount/stop))]
    (log/info component "stopped"))
  (shutdown-agents)
  (log/info "bye"))

(def cli-options
  [["-p" "--port PORT" "Port number"
    :parse-fn #(Integer/parseInt %)]])

(defn start-app
  [args]
  (log/info "Hello, World!")
  (doseq [component (-> args
                        (parse-opts cli-options)
                        mount/start-with-args
                        :started)]
    (log/info component "started"))
  (log/info env)
  (.addShutdownHook (Runtime/getRuntime) (Thread. stop-app))
  (System/exit 0)
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
      (Thread/sleep 60000))))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (start-app args))
