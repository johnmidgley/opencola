(ns opencola.web-ui.view.settings 
  (:require
   [opencola.web-ui.common :as common]
   [opencola.web-ui.view.search :as search]
   [opencola.web-ui.model.error :as error]))

(defn header-actions [query! adding-peer?!]
  [:div.header-actions 
   [:img.header-icon {:src  "../img/add-peer.png" :on-click #(swap! adding-peer?! not)}]
   common/image-divider
   [:img.header-icon {:src  "../img/feed.png" :on-click #(common/set-location (str "#/feed?q=" @query!))}]])

(defn settings-page [query! on-search!]
  (let [adding-peer?! (atom false)]
    (fn []
      [:div.settings-page
       [search/search-header query! on-search! (partial header-actions query! adding-peer?!)]])))
