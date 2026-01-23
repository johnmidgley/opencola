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

(ns opencola.web-ui.view.likes
  (:require [opencola.web-ui.time :refer [format-time pretty-format-time]]
            [opencola.web-ui.view.common :refer [button-component tool-tip]]))

(defn item-like [like-action on-click-authority]
  (let [{authority-name :authorityName
         epoch-second :epochSecond} like-action]
    [:tr.item-attribution
     [:td [:span.authority {:on-click #(on-click-authority authority-name)} authority-name ":"]]
     [:td "("(pretty-format-time epoch-second)")"
      [tool-tip {:text (format-time epoch-second) :tip-position "tip-bottom"}]]]))

;; TODO: Templatize this - same for saves and comments
(defn item-likes [expanded?! like-actions on-click-authority]
  (when @expanded?!
    [:div.item-likes
     [:div.list-header "Likes:"]
     [:table
      [:tbody
       (doall (for [like-action like-actions]
                ^{:key like-action} [item-like like-action on-click-authority]))]]]))

(defn like-edit-control [edit-item!]
  [button-component {:class (str "action-button" (when (:like @edit-item!) " action-highlight")) 
                     :icon-class "icon-like"
                     :tool-tip-text "Like"} 
   (fn [] (swap! edit-item! update-in [:like] #(if % nil true)))])