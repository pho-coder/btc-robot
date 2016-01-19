(ns rocks.pho.btc-robot.utils
  (:require [clj-http.client :as client]
            [digest :as digest]
            [clojure.data.json :as json]))

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
