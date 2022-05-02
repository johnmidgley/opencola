(ns ^:figwheel-hooks opencola.web-ui.config
  (:require
   [reagent.dom :as rdom]
   [ajax.core :refer [GET]]))

(defonce config (atom {}))

(defn get-config [success-handler error-handler]
  (GET "config.json" {:handler #(do (reset! config %)
                                    (success-handler %))
                      :response-format :json
                      :keywords? true
                      :error-handler error-handler}))

(defn get-service-url [] (@config :service-url))




