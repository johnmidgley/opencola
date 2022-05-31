(ns opencola.web-ui.view.settings 
  (:require
   [opencola.web-ui.common :as common]
   [opencola.web-ui.view.search :as search]
   [opencola.web-ui.model.error :as error]
   [cljs.pprint :as pprint]))

(defn header-actions [query! adding-peer?!]
  [:div.header-actions 
   [:img.header-icon {:src  "../img/add-peer.png" :on-click #(swap! adding-peer?! not)}]
   common/image-divider
   [:img.header-icon {:src  "../img/feed.png" :on-click #(common/set-location (str "#/feed?q=" @query!))}]])


(defn peer [peer]
  [:div.feed-item
   [:div.peer-name "Name: "(:name peer)]
   [:div.peer-id "Id: " (:id peer)]
   [:div.peer-public-key "Public Key: "(:publicKey peer)]
   [:div.peer-img-uri "Image: " (:imageUri peer)]
   [:div.peer-acitve "Active? " (str (:isActive peer))]])

(defn peers [peers]
  [:div.peeers 
   (doall (for [p (:results peers)]
            ^{:key p} [peer p]))])

(defn settings-page [query! on-search! peers!]
  (let [adding-peer?! (atom false)]
    (fn []
      [:div.settings-page
       [search/search-header query! on-search! (partial header-actions query! adding-peer?!)]
       [peers @peers!]])))
