(ns opencola.web-ui.view.saves 
  (:require [opencola.web-ui.model.feed :as model]
            [opencola.web-ui.time :refer [format-time]]
            [opencola.web-ui.view.common :refer [action-img]]))

(defn data-url [data-id]
  (when data-id
    (str "/data/" data-id)))

(defn item-save [save-action on-click-authority]
  (let [{authority-name :authorityName
         epoch-second :epochSecond
         data-id :id} save-action]
    [:tr.item-attribution
     [:td [:span.authority {:on-click #(on-click-authority authority-name)} authority-name] " "]
     [:td (format-time epoch-second)]
     [:td
      (when data-id
        [:span
         [:a.action-link  {:href (data-url data-id) :target "_blank"} [action-img "archive"]]])]]))

(defn item-saves [expanded?! save-actions on-click-authority]
  (when @expanded?!
    [:div.item-saves
     [:div.list-header "Saves:"]
     [:table
      [:tbody
       (doall (for [save-action save-actions]
                ^{:key save-action} [item-save save-action on-click-authority]))]]]))

(defn save-item [context persona-id item on-success on-error]
  (let [actions (-> item :activities :save)
        saved? (some #(= persona-id (:authorityId %)) actions)]
    (when (not saved?)
      (model/save-entity
       context
       persona-id
       item
       #(on-success %)
       #(on-error %)))))