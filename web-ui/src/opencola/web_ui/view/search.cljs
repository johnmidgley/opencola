; Copyright 2024 OpenCola
; 
; Licensed under the Apache License, Version 2.0 (the "License");
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
; 
;    http://www.apache.org/licenses/LICENSE-2.0
;
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.

(ns ^:figwheel-hooks opencola.web-ui.view.search 
  (:require
   [opencola.web-ui.location :as location] 
   [opencola.web-ui.view.common :refer [button-component select-menu]] 
   [reagent.core :as r]))

(defn search-box [query! on-enter]
  (fn []
    [:div.search-wrapper
     [:div.form-wrapper
      [button-component {:class "search-button" :icon-class "icon-search"} #(on-enter @query!)]
      [:input.reset.text-input
       {:type "text"
        :name "search-input"
        :value @query!
        :placeholder "Search..."
        :on-change #(reset! query! (-> % .-target .-value))
        :on-keyUp #(when (= (.-key %) "Enter")
                     (on-enter @query!))}]]]))

(defn persona-select-menu [personas! persona-id! on-select!] 
  (fn []
    (let [persona-list (cons {:name "All" :id nil} (:items @personas!))] 
      [select-menu
       {:class "persona-select-menu"}
       persona-list
       persona-id!
       :id :name on-select!])))

(defn header-menu 
  [menu-open?!] 
  [:div.menu
   [:div.toggled-element.overlay {:data-visible @menu-open?! :on-click #(reset! menu-open?! false)}]
   [:div.toggled-element.container {:data-visible @menu-open?!}
    [:div.menu-control
     [:span.title "Menu"]
     [button-component {:class "action-button" :icon-class "icon-close"}
      #(swap! menu-open?! not)]
     ]
    [button-component {:class "menu-item" :text "Feed" :icon-class "icon-feed"}  #(location/set-page! :feed)]
    [button-component {:class "menu-item" :text "Personas" :icon-class "icon-persona"} #(location/set-page! :personas)]
    [button-component {:class "menu-item" :text "Peers" :icon-class "icon-peers"} #(location/set-page! :peers)] 
    [button-component {:class "menu-item" :text "Settings" :icon-class "icon-settings"} #(location/set-page! :settings)]
    [:a.reset.menu-item..button.button-component {:href "help/help.html" :target "_blank"}
     [:span.button-icon {:class "icon-help"}]
     [:span "Help"]]
    [button-component {:class "menu-item caution-color" :text "Log out" :icon-class "icon-logout"} #(location/set-location "/logout")]]]
  )

(defn header-actions [page adding-item?! menu-open?!] 
  [:div.container.header-actions
   (when (= page :feed)
     [:div.container
      [button-component {:class "action-button" :icon-class "icon-new-post" :tool-tip-text "Add new post" :tip-position "tip-bottom"} #(swap! adding-item?! not)] 
      [button-component {:class "action-button" :icon-class "icon-peers" :tool-tip-text "Peers page" :tip-position "tip-bottom"} #(location/set-page! :peers)]])
   (when (= page :peers)
     [:div.container
      [button-component {:class "action-button" :icon-class "icon-new-peer" :tool-tip-text "Add new peer" :tip-position "tip-bottom"} #(swap! adding-item?! not)]
      [button-component {:class "action-button" :icon-class "icon-feed" :tool-tip-text "Feed page" :tip-position "tip-bottom"} #(location/set-page! :feed)]])
   (when (= page :personas)
     [:div.container
      [button-component {:class "action-button" :icon-class "icon-new-persona" :tool-tip-text "Add new persona" :tip-position "tip-bottom"} #(swap! adding-item?! not)]
      [button-component {:class "action-button" :icon-class "icon-feed" :tool-tip-text "Feed page" :tip-position "tip-bottom"} #(location/set-page! :feed)]])
   [button-component {:class "action-button" :icon-class "icon-menu" :tool-tip-text "Menu" :tip-position "tip-bottom"} #(swap! menu-open?! not)]])


(defn search-header [page personas! persona-id! on-persona-select query! on-enter adding-item?!]
  (let [menu-open?! (r/atom false)]
   (fn [] 
     [:nav.nav-bar 
      [:div.left-nav.container
       [:div.fs-0.brand {:on-click #(do (reset! persona-id! nil) (reset! query! "") (location/set-page! :feed))} [:img.logo {:src "../img/pull-tab.png"}]] 
       (when (not= :personas page) [persona-select-menu personas! persona-id! on-persona-select])]
      [search-box query! on-enter]
      [header-actions page adding-item?! menu-open?!] 
      [header-menu menu-open?!]])))
