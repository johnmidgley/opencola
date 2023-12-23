(ns opencola.web-ui.view.settings
  (:require
   [reagent.core :as r]
   [opencola.web-ui.app-state :as state]
   [opencola.web-ui.model.persona :as model]
   [opencola.web-ui.view.common :refer [error-control edit-control-buttons button-component select-menu]]
   [opencola.web-ui.view.search :as search]
   [opencola.web-ui.location :as location]))

(def category-ids (list "Appearance"))

(defn category-selector [on-click]
  [:div.category-selector
   [:h2 "Settings"]
   (doall (for [item category-ids]
            ^{:key item} [button-component {:class "category-selector-button" :text item} #(on-click item)]))])

(defn c-appearance [themes! theme-id!]
  (let [theme-list (map #(hash-map :id (first %) :name (first %)) @themes!)
        current-theme {:id @theme-id! :name @theme-id!}]
    (fn [] 
      [:div.settings-category.appearance
       [:h3.category-title "Appearance"]
       [select-menu {} theme-list current-theme :name :id #(reset! theme-id! %)]])))

(defn categories [themes! theme!]
  (let [current-category! (r/atom (first category-ids))]
    (fn [] 
      [:div.settings-categories
       [category-selector #(reset! current-category! %)]
       [c-appearance themes! theme!]])))


(defn settings-page [personas! persona! themes! theme! on-persona-select query! on-search!]
  (let [adding-persona?! (r/atom false)]
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
       [categories themes! theme!]])))