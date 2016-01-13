(ns rocks.pho.btc-robot.core
  (:gen-class)

  (:require [clj-http.client :as client]))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!")
  (println (client/get "http://api.huobi.com/staticmarket/btc_kline_001_json.js")))
