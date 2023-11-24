(ns opencola.web-ui.view.tags
  (:require [clojure.string :as str]
            [opencola.web-ui.time :refer [format-time]]))

(defn tag [name on-click]
  (let [params (if on-click {:on-click #(on-click name)} {})]
    [:span.tag params name]))

(defn item-tags-summary [actions on-click]
  (when (not-empty actions)
    [:div.tags
     (doall (for [name (distinct (map :value actions))]
              ^{:key name} [tag name on-click]))]))

(defn item-tags-summary-from-string [tags-string]
  (when (not (str/blank? tags-string))
    (let [tags (filter #(seq %) (str/split tags-string #"\s+"))] 
      [:div.tags
       (interpose " "
                  (doall (for [name (distinct tags)]
                           ^{:key name} [tag name])))])))

(defn item-tag [tag-action on-click-authority on-click-tag]
  (let [{authority-name :authorityName
         epoch-second :epochSecond} tag-action]
    [:tr.item-attribution
     [:td [:span.authority {:on-click #(on-click-authority authority-name)} authority-name]]
     [:td (format-time epoch-second)]
     [:td.tag-cell (tag (:value tag-action) on-click-tag)]]))

(defn item-tags [expanded?! actions on-click-authority on-click-tag]
  (when @expanded?!
    [:div.item-tags
     [:div.list-header "Tags:"]
     [:table
      [:tbody
       (doall (for [action actions]
                ^{:key action} [item-tag action on-click-authority on-click-tag]))]]]))