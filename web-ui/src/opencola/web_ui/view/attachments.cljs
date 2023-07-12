(ns opencola.web-ui.view.attachments
  (:require [opencola.web-ui.ajax :as ajax]
            [opencola.web-ui.time :refer [format-time]]
            [opencola.web-ui.view.common :refer [action-img img image?
                                                 keyed-divider]]))

(defn item-attachment [action on-delete]
  (let [{authority-name :authorityName
         epoch-second :epochSecond
         value :value
         id :id} action]
    [:tr.item-attribution
     [:td authority-name]
     [:td (format-time epoch-second)]
     [:td [:a {:href (ajax/resolve-service-url (str "data/" id)) :target "blank"} value]]
     [:td [:span {:on-click #(on-delete id)} (action-img "delete")]]]))

(defn item-attachments [expanded?! attach-actions on-delete]
  (when @expanded?!
    [:div.item-attachments
     [:div.list-header "Attachments:"]
     [:table
      [:tbody
       (doall (for [action attach-actions]
                ^{:key action} [item-attachment action on-delete]))]]]))

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
  (let [groups (group-by attachment-is-image? attachments)
        images (get groups true)
        other (get groups false)] 
    [:div.attachments-preview
     (doall (for [attachment images]
             ^{:key attachment} [image-preview attachment]))
     (when show-other?
       (doall (interpose (keyed-divider)
               (for [attachment other]
                 ^{:key attachment} [attachment-preview attachment]))))]))