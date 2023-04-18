(ns ^:figwheel-hooks opencola.web-ui.model.error 
  (:require
   [reagent.core :as reagent :refer [atom]]))

(defn error-result->str [e]
  (let [{:keys [status response]} e]
    (if (= 0 status)
      "Unable to connect to server. Please make sure it is running."
      (or (:message response) status))))

(defn set-error [m error]
  (assoc-in m [:error] error))

(defn get-error [m]
  (:error m))

(defn clear-error [item]
  (dissoc item :error))

(defn set-error! [atom! error]
  (reset! atom! (set-error @atom! error)))

(defn error-control [m]
  (when-let [e (get-error m)]
    [:div.error [:p e]]))

(defn error [e!]
  (when @e!
    [:div.error [:p @e!]]))



