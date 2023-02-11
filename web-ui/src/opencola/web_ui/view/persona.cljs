(ns opencola.web-ui.view.persona
  (:require
   [reagent.core :as reagent :refer [atom]]
   [opencola.web-ui.model.persona :as model]
   [opencola.web-ui.model.error :as error]))

(defn get-personas [personas!]
  (model/get-personas
   #(reset! personas! %)
   #(error/set-error! personas! %)))


