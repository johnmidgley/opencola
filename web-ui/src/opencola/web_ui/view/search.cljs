(ns ^:figwheel-hooks opencola.web-ui.view.search 
  (:require
   [opencola.web-ui.location :as location] 
   [opencola.web-ui.view.common :refer [img-button anotated-img-button persona-menu]]
   [reagent.core :as r]))


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
    [:div.search-wrapper
     [:div.form-wrapper
      [:img.pointer.button {:src "../img/search.png" :width 20 :height 20 :on-click #(on-enter @query!)}]
      [:input.reset.text-input
       {:type "text"
        :name "search-input"
        :value @query!
        :placeholder "Search..."
        :on-change #(reset! query! (-> % .-target .-value))
        :on-keyUp #(when (= (.-key %) "Enter")
                     (on-enter @query!))}]]]))

(defn header-menu 
  [menu-open?!] 
  [:div.menu
   [:div.toggled-element.overlay {:data-visible @menu-open?! :on-click #(reset! menu-open?! false)}]
   [:div.toggled-element.container {:data-visible @menu-open?!}
    [:div.menu-control
     [:span.title "Menu"]
     [img-button "button" "icon" "../img/x.png"
      #(swap! menu-open?! not)]
     ]
    [anotated-img-button "menu-item" "icon" "" "Feed" "../img/feed.png" #(location/set-page! :feed)]
    [anotated-img-button "menu-item" "icon" "" "Personas" "../img/user.png" #(location/set-page! :personas)]
    [anotated-img-button "menu-item" "icon" "" "Peers" "../img/peers.png" #(location/set-page! :peers)] 
    [anotated-img-button "menu-item" "icon" "" "Settings" "../img/settings.png" #(location/set-page! :feed)]
    [:a.reset.menu-item {:href "help/help.html" :target "_blank"}
     [:img {:src "../img/help.png" :data-icon-height "75%" :class "icon"}]
     [:span "Help"]]
    [anotated-img-button "menu-item caution-color" "icon" "" "Log Out" "../img/logout.svg" #(location/set-page! :feed)]]]
  )


(defn search-header [page personas! persona! on-persona-select query! on-enter header-actions] 
  (let [menu-open?! (r/atom false)]
   (fn []
     [:nav.nav-bar
      [:div.container.mr-a
       [:a.fs-0 {:href "#/feed"} [:img.logo {:src "../img/pull-tab.png"}]]
       [persona-select page personas! persona! on-persona-select]]
      [search-box query! on-enter]
      [:div.container.header-actions
       [header-actions]
       [img-button "button" "icon" (if @menu-open?! "../img/x.png" "../img/menu.png")
        #(swap! menu-open?! not)]
       ]
      [header-menu menu-open?!]])))


