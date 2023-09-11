(ns ^:figwheel-hooks opencola.web-ui.model.error 
  (:require
   [reagent.core :as reagent :refer [atom]]))


(defn error-result->str [e]
  (let [{:keys [status response]} e]
    (if (= 0 status)
      "Unable to connect to server. Please make sure it is running."
      (or (:message response) status))))