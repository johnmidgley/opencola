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

(ns opencola.web-ui.view.attachments
  (:require [opencola.web-ui.util :refer [distinct-by]]
            [opencola.web-ui.ajax :as ajax]
            [opencola.web-ui.time :refer [format-time pretty-format-time]]
            [opencola.web-ui.view.common :refer [button-component image? keyed-divider tool-tip]]))

(defn item-attachment [action on-delete on-click-authority]
  (let [{authority-name :authorityName
         epoch-second :epochSecond
         value :value
         id :id} action]
    [:div.item-attribution
     [:span.authority {:on-click #(on-click-authority authority-name)} authority-name]
     [:span "("(pretty-format-time epoch-second)")"]
     [:a {:href (ajax/resolve-service-url (str "data/" id)) :target "blank"} value]
     [tool-tip {:text (format-time epoch-second) :tip-position "tip-bottom"}]
     (when on-delete
       [button-component {:icon-class "icon-delete" :class "action-button" } #(on-delete id)])]))


(defn select-attachment [persona-id! attachments]
  (let [persona-id @persona-id!
        by-persona (first (filter #(= persona-id (:authorityId %)) attachments))
        oldest (first (sort-by :epochSecond attachments))] 
    (or by-persona oldest)))

(defn distinct-attachments [persona-id! attach-actions]
  (let [by-id (group-by :id attach-actions)]
    (for [[_ attachments] by-id] 
      (select-attachment persona-id! attachments))))

(defn item-attachments [persona-id! expanded?! attach-actions on-delete on-click-authority] 
  (when @expanded?!
    (let [actions (distinct-attachments persona-id! attach-actions)]
      [:div.item-attachments
       [:div.list-header "Attachments:"]
       (doall (for [action (->> actions (sort-by :epochSecond) (distinct-by :id))]
                (let [action-authority-id (:authorityId action)
                      on-delete (when (= action-authority-id @persona-id!) on-delete)]
                  ^{:key action} [item-attachment action on-delete on-click-authority])))])))

(defn attachment-is-image? [attachment]
  (let [{:keys [value]} attachment]
    (image? value)))

(defn image-preview [attachment]
  (let [{:keys [id]} attachment] 
    [:div.attachment-preview
     [:a {:href (ajax/resolve-service-url (str "data/" id)) :target "blank"}
      [:img.preview-img {:src (ajax/resolve-service-url (str "data/" id))}]]]))

(defn attachment-preview [attachment]
  (let [{:keys [id value]} attachment] 
    [:span.attachment-preview [:a.attachment-link {:href (ajax/resolve-service-url (str "data/" id)) :target "blank"} value]]))

(defn attachments-preview [attachments show-other?]
  ;; Partition attachments into images and other
  (let [groups (group-by attachment-is-image? (distinct-by :id attachments))
        images (get groups true)
        other (get groups false)] 
    (when (seq attachments) 
     [:div.attachments-preview
           [:div.preview-images
            (doall (for [attachment images]
                     ^{:key attachment} [image-preview attachment]))]
           (when show-other?
             (doall (interpose (keyed-divider)
                               (for [attachment other]
                                 ^{:key attachment} [attachment-preview attachment]))))])))