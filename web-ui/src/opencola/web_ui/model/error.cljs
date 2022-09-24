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

(defn clear-error [item]
  (dissoc item :error))

(defn set-error! [atom! error]
  (reset! atom! (set-error @atom! error)))

(defn error-control [m]
  (if-let [e (get-error m)]
    [:div.error [:p e]]))



