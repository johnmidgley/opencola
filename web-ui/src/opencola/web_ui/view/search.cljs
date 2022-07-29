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

(defn search-header [query! on-enter header-actions]
  [:div.search-header 
   [header-actions]
   [:table
    [:tbody
     [:tr
      [:td [:img {:src "../img/pull-tab.png" :width 50 :height 50 :on-click #(common/set-location "") }]]
      [:td [search-box query! on-enter]]]]]])


