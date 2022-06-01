(ns ^:figwheel-hooks opencola.web-ui.model.error 
  (:require
   [reagent.core :as reagent :refer [atom]]))

(defonce error-message (atom nil))

(defn get-error-message []
  @error-message)

(defn set-error-message [message]
  (reset! error-message message)
  (.log js/console message))

;; TODO: Clear error on any successful call
; Generic error handler that sets error message
(defn error-handler [{:keys [status status-text]}]
  (set-error-message (str "Error: " status ": " status-text)))

