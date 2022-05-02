(ns ^:figwheel-hooks opencola.web-ui.config
  (:require
   [ajax.core :refer [GET]]))

(defn get-config [success-handler error-handler]
  (GET "config.json" {:handler success-handler
                      :response-format :json
                      :keywords? true
                      :error-handler error-handler}))


