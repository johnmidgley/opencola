(ns opencola.web-ui.view.saves 
  (:require [opencola.web-ui.time :refer [format-time]]
            [opencola.web-ui.view.common :refer [action-img]]))

(defn data-url [data-id]
  (when data-id
    (str "/data/" data-id)))

(defn item-save [save-action]
  (let [{authority-name :authorityName
         epoch-second :epochSecond
         data-id :id} save-action]
    [:tr.item-attribution
     [:td authority-name " "]
     [:td (format-time epoch-second)]
     [:td
      (when data-id
        [:span
         [:a.action-link  {:href (data-url data-id) :target "_blank"} [action-img "archive"]]])]]))

(defn item-saves [expanded?! save-actions]
  (when @expanded?!
    [:div.item-saves
     [:div.list-header "Saves:"]
     [:table
      [:tbody
       (doall (for [save-action save-actions]
                ^{:key save-action} [item-save save-action]))]]]))