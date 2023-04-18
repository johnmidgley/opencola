(ns opencola.web-ui.view.attachments
  (:require [clojure.string :as string]
            [opencola.web-ui.ajax :as ajax]
            [opencola.web-ui.time :refer [format-time]]
            [opencola.web-ui.view.common :refer [action-img keyed-divider]]
            [reagent.core :as reagent :refer [atom]]))

;; TODO: Make this a table with file size
(defn selected-files-table [file-list!]
  (fn []
    (when (some? @file-list!)
      [:ul (map-indexed (fn [i file]
                          [:li {:key i} (.-name file)])
                        @file-list!)])))

(defn select-files-control [file-list!]
  (fn []
    (let [input-id (str (random-uuid))]
      [:span
       [:input {:type "file"
                :id input-id
                :multiple true
                :style {:display "none"}
                :on-change #(reset! file-list! (.. % -target -files))}]
       [:button {:on-click #(.click (js/document.getElementById input-id))} "Select Files"]])))

(defn attachment-control [expanded?! on-attach]
  (when @expanded?!
    (let [file-list! (atom [])]
      [:div.attachment-control
       [:div.attachment-control-header "Add Attachments:"]
       [selected-files-table file-list!]
       [select-files-control file-list!] " "
       [:button {:on-click #(on-attach @file-list!)} "Attach"] " "
       [:button {:on-click (fn [] (swap! expanded?! #(not %)))} "Cancel"]])))

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

(defn extenstion [filename]
  (let [parts (string/split filename #"\.")]
    (last parts)))

(defn image? [filename]
  (contains? #{"jpg" "jpeg" "png" "gif"} (extenstion filename)))

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
    [:span [:a.attachment-link {:href (ajax/resolve-service-url (str "data/" id)) :target "blank"} value]]))

(defn attachments-preview [attachments]
  ;; Partition attachments into images and other
  (let [groups (group-by attachment-is-image? attachments)
        images (get groups true)
        other (get groups false)] 
    [:div.attachments-preview
     (doall (for [attachment images]
             ^{:key attachment} [image-preview attachment]))
     (doall (interpose (keyed-divider)
                  (for [attachment other]
                    ^{:key attachment} [attachment-preview attachment])))]))