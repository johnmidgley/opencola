(ns ^:figwheel-hooks opencola.web-ui.view.search 
  (:require
   [reagent.core :as reagent :refer [atom]]
   [opencola.web-ui.common :as common]))


(defn search-box [query! on-enter]
  (fn []
    [:div.search-box>input.search-input
     {:type "text"
      :value @query!
      :on-change #(reset! query! (-> % .-target .-value))
      :on-keyUp #(if (= (.-key %) "Enter")
                   (on-enter @query!))}]))

(defn persona-select [personas!]
      [:td [:select {:id "persona-select" :title "Persona"}
            (doall (for [persona @personas!]
                ^{:key persona} [:option  {:value (:id persona)} (:name persona)]))
            ^{:key "new"} [:option {:value "new"} "New..."]]])

(defn search-header [personas! query! on-enter header-actions]
  [:div.search-header 
   [header-actions]
   [:table
    [:tbody
     [:tr
      [:td [:img {:src "../img/pull-tab.png" :width 50 :height 50 :on-click #(common/set-location "") }]]
      [persona-select personas!]
      [:td [search-box query! on-enter]]]]]])


