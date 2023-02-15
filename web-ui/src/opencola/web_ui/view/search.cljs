(ns ^:figwheel-hooks opencola.web-ui.view.search 
  (:require
   [reagent.core :as reagent :refer [atom]]
   [opencola.web-ui.location :as location]))


(defn persona-select [personas! persona! on-select]
  (fn [] 
    [:td [:select 
          {:id "persona-select" 
           :title "Persona"
           :on-change #(on-select (-> % .-target .-value))
           :value (if @persona! @persona! "manage")}
          (doall (for [persona @personas!]
                   ^{:key persona} [:option  {:value (:id persona)} (:name persona)]))
          (if (not-empty @personas!) ;; Avoid flicker on init
            ^{:key "manage"} [:option {:value "manage"} "Manage..."])]]))

(defn search-box [query! on-enter]
  (fn []
    [:div.search-box>input.search-input
     {:type "text"
      :value @query!
      :on-change #(reset! query! (-> % .-target .-value))
      :on-keyUp #(if (= (.-key %) "Enter")
                   (on-enter @query!))}]))


(defn search-header [personas! persona! on-persona-select query! on-enter header-actions]
  [:div.search-header 
   [header-actions]
   [:table
    [:tbody
     [:tr
      [:td [:img {:src "../img/pull-tab.png" :width 50 :height 50 :on-click #(location/set-location "") }]]
      [persona-select personas! persona! on-persona-select]
      [:td [search-box query! on-enter]]]]]])


