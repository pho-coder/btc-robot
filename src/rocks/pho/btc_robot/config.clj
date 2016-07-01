(ns rocks.pho.btc-robot.config
  (:require [cprop.core :refer [load-config]]
            [cprop.source :as source]
            [mount.core :refer [args defstate]]))

(defstate env :start (load-config
                      :merge
                      [(args)
                       (source/from-env)]))
