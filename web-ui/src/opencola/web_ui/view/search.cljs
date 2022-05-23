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

(defn search-header [on-enter creating-post?!]
  [:div.search-header 
   [:img {:src "../img/pull-tab.png" :width 50 :height 50}]
   "openCola"
   #_[:div.add-item 
    {:on-click #(swap! creating-post?! not)}
    [:img.new-post {:src  "../img/new-post.png"}]]
   [search-box on-enter]])


