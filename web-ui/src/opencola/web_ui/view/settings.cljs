(ns opencola.web-ui.view.settings 
  (:require
   [opencola.web-ui.common :as common]
   [opencola.web-ui.view.search :as search]
   [opencola.web-ui.model.error :as error]))

(defn settings-page []
  (fn []
    [:div.settings-page
     "Settings"]))
