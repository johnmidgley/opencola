(ns ^:figwheel-hooks opencola.web-ui.config
  (:require
   [reagent.dom :as rdom]
   [ajax.core :refer [GET]]
   [opencola.web-ui.model.error :as error]))

(defonce config (atom nil))

(defn get-config [on-success on-error]
  (GET "config.json" {:handler #(do (reset! config %)
                                    (on-success %))
                      :response-format :json
                      :keywords? true
                      :error-handler #(on-error (error/error-result->str %))}))

(defn get-service-url [] (@config :service-url))




