(ns opencola.web-ui.view.settings 
  (:require
   [reagent.core :as reagent :refer [atom]]
   [opencola.web-ui.common :as common :refer [action-img]]
   [opencola.web-ui.model.settings :as model]
   [opencola.web-ui.view.search :as search]
   [opencola.web-ui.model.error :as error]
   [cljs.pprint :as pprint]))

(defn header-actions [query! adding-peer?!]
  [:div.header-actions 
   [:img.header-icon {:src  "../img/add-peer.png" :on-click #(swap! adding-peer?! not)}]
   common/image-divider
   [:img.header-icon {:src  "../img/feed.png" :on-click #(common/set-location (str "#/feed?q=" @query!))}]])


(defn peer-value [peer! key editing?]
  [:div.peer-value
   (if editing?
     [:input.peer-value
      {:type "text"
       :value (key @peer!)
       :on-change #(swap! peer! assoc-in [key]  (-> % .-target .-value))}]
     [:span (key @peer!)])])

(defn peer-active [peer! editing?!]
  (if @editing?!
    [:input
     {:type "checkbox"
      :checked (:isActive @peer!)
      :on-change #(swap! peer! assoc-in [:isActive] (-> % .-target .-checked))}]
    (str (:isActive @peer!))))

(defn peer-item [peers! peer]
  (let [editing?! (atom false)
        p! (atom peer)]
    (fn []
      (let [image-uri (:imageUri @p!)]
        [:div.peer-item
         [:div.peer-img-box
          [:img.peer-img 
           {:src (if (not (empty? image-uri)) image-uri "../img/user.png")}]]
         [:div.peer-info
          [:table
           [:tbody
            [:tr [:td.peer-name [action-img "user"]] [:td [peer-value p! :name @editing?!]]]
            [:tr [:td.peer-id [action-img "id"]] [:td [:span.id (:id peer)]]]
            [:tr [:td.peer-public-key [action-img "key"]] [:td [:span.key [peer-value p! :publicKey @editing?!]]]]
            [:tr [:td.peer-address [action-img "link"]] [:td [:span.uri [peer-value p! :address @editing?!]]]]
            [:tr [:td.peer-img-uri [action-img "photo"]] [:td [:span.uri [peer-value p! :imageUri @editing?!]]]]
            [:tr [:td.peer-acitve [action-img "refresh"]] [:td [peer-active p! editing?!]]]]]]
         (if @editing?!
           [:div
            [:button {:on-click #(model/update-peer peers! @p!)} "Save"] " "
            [:button {:on-click  #(do
                                    (reset! p! peer)
                                    (reset! editing?! false))} "Cancel"] " "
            [:button.delete-button {:on-click #(model/delete-peer peers! peer)} "Delete"]]
           [:div.edit-peer
            [:button {:on-click #(reset! editing?! true)} "Edit"]])]))))


(defn peer-list [peers!]
  (if @peers!
    [:div.peers 
     (doall (for [peer (:results @peers!)]
              ^{:key peer} [peer-item peers! peer]))]))

(defn settings-page [peers! query! on-search!]
  (let [adding-peer?! (atom false)]
    (fn []
      [:div.settings-page
       [search/search-header query! on-search! (partial header-actions query! adding-peer?!)]
       [common/error-message]
       [peer-list peers!]])))

