; Copyright 2024-2026 OpenCola
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

(ns opencola.web-ui.view.tags
  (:require [clojure.string :as str]
            [opencola.web-ui.time :refer [format-time pretty-format-time]]
            [opencola.web-ui.view.common :refer [tool-tip]]))

(defn tag [name on-click]
  (let [params (if on-click {:on-click #(on-click name)} {})]
    [:span.tag params name]))

(defn item-tags-summary [actions on-click]
  (when (not-empty actions)
    [:div.tags
     (doall (for [name (distinct (map :value actions))]
              ^{:key name} [tag name on-click]))]))

(defn item-tags-summary-from-string [tags-string]
  (when (not (str/blank? tags-string))
    (let [tags (filter #(seq %) (str/split tags-string #"\s+"))] 
      [:div.tags
       (interpose " "
                  (doall (for [name (distinct tags)]
                           ^{:key name} [tag name])))])))

(defn item-tag [tag-action on-click-authority on-click-tag]
  (let [{authority-name :authorityName
         epoch-second :epochSecond} tag-action]
    [:tr.item-attribution
     [:td [:span.authority {:on-click #(on-click-authority authority-name)} authority-name]]
     [:td "("(pretty-format-time epoch-second)")"
      [tool-tip {:text (format-time epoch-second) :tip-position "tip-bottom"}]] 
     [:td.tag-cell (tag (:value tag-action) on-click-tag)]]))

(defn item-tags [expanded?! actions on-click-authority on-click-tag]
  (when @expanded?!
    [:div.item-tags
     [:div.list-header "Tags:"]
     [:table
      [:tbody
       (doall (for [action actions]
                ^{:key action} [item-tag action on-click-authority on-click-tag]))]]]))