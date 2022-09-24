(ns ^:figwheel-hooks opencola.web-ui.model.error 
  (:require
   [reagent.core :as reagent :refer [atom]]))

(defn error-result->str [{status :status text :status-text response :response}]
  (if (= 0 status)
    "Unable to connect to server. Please make sure it is running."
    (:message response)))

(defn set-error [m error]
  (assoc-in m [:error] error))

(defn get-error [m]
  (:error m))

(defn set-error! [atom! error]
  (reset! atom! (set-error @atom! error)))

(defn error-control [m]
  (if-let [e (get-error m)]
    [:div.error [:p e]]))


;; TODO - Remove everything below here - depended on global error state

(defonce error-message (atom nil))

(defn set-error-message [message]
  (reset! error-message message)
  (.log js/console message))

;; TODO: Clear error on any successful call
; Generic error handler that sets error message
(defn error-handler [{:keys [status status-text]}]
  (set-error-message (str "Error: " status ": " status-text)))

