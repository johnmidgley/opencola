(ns opencola.web-ui.view.attachments
  (:require
   [reagent.core :as reagent :refer [atom]]
   [opencola.web-ui.time :refer [format-time]]
   [opencola.web-ui.ajax :as ajax]
   [opencola.web-ui.model.feed :as model]))

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

(defn item-attachment [action]
  (let [{authority-name :authorityName
         epoch-second :epochSecond
         value :value
         id :id} action]
    [:tr.item-attribution
     [:td authority-name]
     [:td (format-time epoch-second)]
     [:td [:a {:href (ajax/resolve-service-url (str "data/" id)) :target "blank"} value]]]))

(defn item-attachments [expanded?! attach-actions]
  (when @expanded?!
    [:div.item-attachments
     [:div.list-header "Attachments:"]
     [:table
      [:tbody
       (doall (for [action attach-actions]
                ^{:key action} [item-attachment action]))]]]))