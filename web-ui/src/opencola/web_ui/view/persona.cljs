(ns opencola.web-ui.view.persona
  (:require
   [reagent.core :as reagent :refer [atom]]
   [opencola.web-ui.model.persona :as model]
   [opencola.web-ui.app-state :as state :refer [persona!]]
   [opencola.web-ui.model.error :as error]))

(defn init-personas [personas! on-success on-error]
  (model/get-personas
   (fn [personas] 
     (reset! personas! personas)
     (on-success))
   #(on-error %)))

(defn get-personas [personas!]
  (model/get-personas
   #(reset! personas! %)
   #(error/set-error! personas! %)))


