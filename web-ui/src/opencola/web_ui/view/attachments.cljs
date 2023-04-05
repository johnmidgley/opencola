(ns opencola.web-ui.view.attachments 
  (:require
   [reagent.core :as reagent :refer [atom]]
   [opencola.web-ui.time :refer [format-time]]
   [opencola.web-ui.ajax :as ajax]))

(defn selected-files-table [file-list!]
  (fn []
    (if (some? @file-list!)
      [:ul (map-indexed (fn [i file]
                          (js/console.log file)
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

(defn attachment-control [persona-id! feed! entity-id expanded?!]
  (if @expanded?!
    (let [file-list! (atom [])]
      [:div.attachment-control
       [:div.attachment-control-header "Attachments:"]
       [selected-files-table file-list!]
       [select-files-control file-list!]
       " "
       [:button {:on-click (fn [] (ajax/upload-files (str "/upload?personaId=" @persona-id!) @file-list!))} "Attach"]
       " "
       [:button {:on-click (fn [] (swap! expanded?! #(not %)))} "Cancel"]])))

(defn item-attachment [action]
  (let [{authority-name :authorityName
         epoch-second :epochSecond
         value :value} action]
    [:tr.item-attribution
     [:td (str authority-name)]
     [:td (format-time epoch-second)]
     [:td (str value)]]))

(defn item-attachments [expanded?! attach-actions]
  (if (and @expanded?!) 
    [:div.item-attachments
     [:div.list-header "Attachments:"]
     [:table
      [:tbody
       (doall (for [action attach-actions]
                ^{:key action} [item-attachment action]))]]]))