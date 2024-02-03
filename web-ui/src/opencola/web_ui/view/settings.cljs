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

(ns opencola.web-ui.view.settings
  (:require
   [reagent.core :as r]
   [opencola.web-ui.app-state :as state] 
   [opencola.web-ui.view.common :refer [error-control button-component select-menu]]
   [opencola.web-ui.view.search :as search]
   [opencola.web-ui.theme :refer [theme-names]]))

(def category-ids (list "Appearance"))

(defn category-selector [on-click]
  [:div.category-selector
   (doall (for [item category-ids]
            ^{:key item} [button-component {:class "category-selector-button" :text item} #(on-click item)]))])

;; TODO: themes! will be needed when active customization and creation of themes is implemented
(defn c-appearance [themes! theme-id!]
  (let [theme-list (map #(hash-map :id % :name %) (theme-names))]
    (fn [] 
      [:div.settings-category.appearance
       [:h3.category-title "Appearance"]
       [:div.setting-row
        [:span.setting-title "Theme:"]
        [select-menu {} theme-list theme-id! :name :id #(state/theme! %)]]])))

(defn categories [themes! theme!] 
  (fn [] 
    [:div.settings-categories
       ;;[category-selector #(reset! current-category! %)]
     [c-appearance themes! theme!]]))


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
       [:h2.text-center "Settings"]
       [error-control (state/error!)] 
       [categories themes! theme!]])))