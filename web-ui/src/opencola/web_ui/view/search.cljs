(ns ^:figwheel-hooks opencola.web-ui.view.search 
  (:require
   [reagent.core :as reagent :refer [atom]]))


(defn search-box [on-enter]
  (let [query (atom "")]
    (fn []
      [:div.search-box>input
       {:type "text"
        :value @query
        :on-change #(reset! query (-> % .-target .-value))
        :on-keyUp #(if (= (.-key %) "Enter")
                     (on-enter @query))}])))

(defn search-header [on-enter]
  [:div.search-header 
   [:img {:src "../img/pull-tab.png" :width 50 :height 50}]
   "openCola"
   [search-box on-enter]])


