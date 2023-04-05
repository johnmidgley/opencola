(ns opencola.web-ui.view.attachments 
  (:require
   [opencola.web-ui.ajax :as ajax] ;; TODO: Move out to file namespace
   [reagent.core :as reagent :refer [atom]]))

(defn select-files-control [file-list!]
  (fn []
    [:div
     [:input {:type "file"
              :multiple true
              :on-change #(reset! file-list! (.. % -target -files))}]
     #_[:ul (map-indexed (fn [i file]
                         [:li {:key i} (.-name file)])
                       @file-list!)]]))

(defn attachment-control [persona-id! feed! entity-id expanded?!]
  (if @expanded?!
  (let [file-list! (atom [])]
    [:div.attachment-control
     [:div.attachment-control-header "Add attachment:"]
     [select-files-control file-list!]
     [:div.attachment-control-footer
      [:button {:on-click (fn [] (swap! expanded?! #(not %)))} "Cancel"]
      [:button {:on-click (fn [] 
        (ajax/upload-files (str "/upload?personaId=" @persona-id!) @file-list!)
      )} "Save"]]])))

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