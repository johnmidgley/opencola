(ns ^:figwheel-hooks opencola.web-ui.view.search 
  (:require
   [opencola.web-ui.location :as location]))


(defn selected-persona [page persona!]
  (case page
    :personas "manage"
    (if @persona! @persona! "all")))

(defn persona-select [page personas! persona! on-select]
  (fn [] 
    [:div [:select.persona-select 
          {
           :name "select-persona"
           :title "Persona"
           :on-change #(on-select (-> % .-target .-value))
           :value (selected-persona page persona!)}
          ^{:key "all"} [:option {:value "" :disabled (= page :peers)} "All"]
          (doall (for [persona (:items @personas!)]
                   ^{:key persona} [:option  {:value (:id persona)} (:name persona)]))
          (when (not-empty @personas!) ;; Avoid flicker on init
            ^{:key "manage"} [:option {:value "manage"} "Manage..."])]]))

(defn search-box [query! on-enter]
  (fn []
    [:div.search-box
    [:img.pointer {:src "../img/search.png" :width 20 :height 20 :on-click #(on-enter @query!)}]
    [:input.search-input.reset
     {:type "text"
      :name "search-input"
      :value @query!
      :placeholder "Search..."
      :on-change #(reset! query! (-> % .-target .-value))
      :on-keyUp #(when (= (.-key %) "Enter")
                   (on-enter @query!))}]]))


(defn search-header [page personas! persona! on-persona-select query! on-enter header-actions]
  [:div.nav-bar
   [:div.icon-group
      [:a.fs-0 {:href "#/feed"} [:img.logo {:src "../img/pull-tab.png"}]]
      [persona-select page personas! persona! on-persona-select]]
    [search-box query! on-enter]
    [header-actions]])


