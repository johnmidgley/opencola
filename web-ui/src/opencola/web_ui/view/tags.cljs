(ns opencola.web-ui.view.tags
  (:require [clojure.string :as str]
            [opencola.web-ui.time :refer [format-time]]))

(defn tag [name]
  [:span.tag name])

(defn item-tags-summary [actions]
  (when (not-empty actions)
    [:div.tags
     (interpose " "
                (doall (for [name (distinct (map :value actions))]
                         ^{:key name} [tag name])))]))

(defn item-tags-summary-from-string [tags-string]
  (when (not (str/blank? tags-string))
    (let [tags (filter #(seq %) (str/split tags-string #"\s+"))] 
      [:div.tags
       (interpose " "
                  (doall (for [name (distinct tags)]
                           ^{:key name} [tag name])))])))

(defn item-tag [tag-action]
  (let [{authority-name :authorityName
         epoch-second :epochSecond} tag-action]
    [:tr.item-attribution
     [:td authority-name]
     [:td (format-time epoch-second)]
     [:td.tag-cell (tag (:value tag-action))]]))

(defn item-tags [expanded?! actions]
  (when @expanded?!
    [:div.item-tags
     [:div.list-header "Tags:"]
     [:table
      [:tbody
       (doall (for [action actions]
                ^{:key action} [item-tag action]))]]]))