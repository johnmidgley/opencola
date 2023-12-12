(ns opencola.web-ui.view.settings
  (:require
   [reagent.core :as r]
   [opencola.web-ui.app-state :as state]
   [opencola.web-ui.model.persona :as model]
   [opencola.web-ui.view.common :refer [input-checkbox error-control edit-control-buttons button-component text-input-component swap-atom-data! profile-img]]
   [opencola.web-ui.view.search :as search]
   [opencola.web-ui.location :as location]))


(defn settings-page [personas! persona! on-persona-select query! on-search!]
  (let [adding-persona?! (atom false)]
    (fn []
      [:div.settings-page
       [search/search-header
        :personas
        personas!
        persona!
        on-persona-select
        query!
        on-search!
        adding-persona?!]
       [error-control (state/error!)]
       [:h2.text-center "Settings"]])))