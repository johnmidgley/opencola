(ns ^:figwheel-hooks opencola.web-ui.view.search 
  (:require
   [reagent.core :as reagent :refer [atom]]
   [opencola.web-ui.common :as common]))


(defn search-box [query! on-enter]
  (fn []
    [:div.search-box>input
     {:type "text"
      :value @query!
      :on-change #(reset! query! (-> % .-target .-value))
      :on-keyUp #(if (= (.-key %) "Enter")
                   (on-enter @query!))}]))

(defn search-header [query! on-enter creating-post?!]
  [:div.search-header 
   [:img {:src "../img/pull-tab.png" :width 50 :height 50 :on-click #(common/set-location "") }]
   "openCola"
   [:div.add-item 
    {:on-click #(swap! creating-post?! not)}
    [:img.new-post {:src  "../img/new-post.png"}]]
   [search-box query! on-enter]])


