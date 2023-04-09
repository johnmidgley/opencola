(ns opencola.web-ui.view.likes
  (:require [opencola.web-ui.time :refer [format-time]]
            [opencola.web-ui.view.common :refer [action-img]]))

(defn item-like [like-action]
  (let [{authority-name :authorityName
         epoch-second :epochSecond} like-action]
    [:tr.item-attribution
     [:td (str authority-name)]
     [:td (format-time epoch-second)]]))

;; TODO: Templatize this - same for saves and comments
(defn item-likes [expanded?! like-actions]
  (when @expanded?!
    [:div.item-likes
     [:div.list-header "Likes:"]
     [:table
      [:tbody
       (doall (for [like-action like-actions]
                ^{:key like-action} [item-like like-action]))]]]))

(defn like-edit-control [edit-item!]
  [:div.like-edit-control
   [:span.field-header "Like: "]
   [:span {:class (when (:like @edit-item!) "highlight")
           :on-click (fn [] (swap! edit-item! update-in [:like] #(if % nil true)))}
    (action-img "like")]])