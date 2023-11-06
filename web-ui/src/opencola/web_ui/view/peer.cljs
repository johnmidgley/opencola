(ns opencola.web-ui.view.peer 
  (:require 
   [reagent.core :as reagent :refer [atom]]
   [opencola.web-ui.app-state :as state]
   [opencola.web-ui.view.common :refer [action-img image-divider input-text input-checkbox error-control help-control]]
   [opencola.web-ui.model.peer :as model]
   [opencola.web-ui.view.search :as search]
   [opencola.web-ui.location :as location]))


;; These methods are not really view. They are more binders.
(defn get-peers [persona-id peers! on-error]
  (model/get-peers
   persona-id
   #(reset! peers! %)
   #(on-error %)))

(defn update-peer [persona-id peers! peer! on-error]
  (model/update-peer
   persona-id
   (dissoc @peer! :error)
   #(reset! peers! %)
   #(on-error %)))

(defn delete-peer [persona-id peers! peer! on-error]
  (model/delete-peer
   persona-id
   @peer!
   #(reset! peers! %)
   #(on-error %)))

(defn header-actions [adding-peer?!]
  [:div.header-actions 
   [:div.header-button [:img.header-icon {:src  "../img/add-peer.png" :on-click #(swap! adding-peer?! not)}]]
   
   [:div.header-button [:img.header-icon {:src  "../img/feed.png" :on-click #(location/set-page! :feed)}]]
   
   [help-control]])

(defn map-to-token [m]
  (->> m (map (fn [[k v]] (str (name k) "=" v))) (interpose \|) (apply str)))

(defn peer-item [persona-id peers! peer adding-peer?!]
  (let [creating? adding-peer?! ;; TODO - looks like not needed
        editing?! (atom creating?)
        p! (atom peer)
        error! (atom nil)]
    (fn []
      (let [image-uri (:imageUri @p!)]
        [:div.peer-item
         [:div.peer-img-box
          [:img.peer-img 
           {:src (if (seq image-uri) image-uri "../img/user.png")}]]
         [:div.peer-info
          [:table.peer-info
           [:tbody
            [:tr 
             [:td.peer-field [action-img "user"]] 
             [:td [input-text p! :name @editing?!]]]
            [:tr 
             [:td.peer-field [action-img "id"]] 
             [:td [:span.id [input-text p! :id creating?]]]]
            [:tr 
             [:td.peer-field [action-img "key"]] 
             [:td [:span.key [input-text p! :publicKey @editing?!]]]]
            [:tr 
             [:td.peer-field [action-img "link"]] 
             [:td [:span.uri [input-text p! :address @editing?!]]]]
            [:tr 
             [:td.peer-field [action-img "photo"]] 
             [:td [:span.uri [input-text p! :imageUri @editing?!]]]]
            [:tr 
             [:td.peer-field [action-img "refresh"]] 
             [:td [input-checkbox p! :isActive @editing?!]]]]]]
         (if @editing?!
           [:div
            [error-control error!]
            [:button
             {:disabled (and (not adding-peer?!) (= @p! peer)) 
              :on-click (fn []
                           (update-peer persona-id peers! p! #(do (println "ERRORO") (reset! error! %)))
                           (when adding-peer?! (reset! adding-peer?! false)))} "Save"] " "
            [:button {:on-click  #(do
                                    (reset! p! peer)
                                    (when adding-peer?! (reset! adding-peer?! false))
                                    (reset! editing?! false))} "Cancel"] " "
            (when (not creating?)
              [:button.delete-button {:on-click (fn [] ( delete-peer persona-id peers! p! #(reset! error! %)))} "Delete"])]
           [:div.edit-peer
            [:button {:on-click #(reset! editing?! true)} "Edit"]])]))))

(defn get-invite-token [persona-id token! on-error]
  (model/get-invite-token
   persona-id
   #(reset! token! %)
   #(on-error %)))

(defn add-peer-item [persona-id peers! adding-peer?!]
  (let [send-token! (atom "Loading...")
        receive-token! (atom "")
        peer! (atom nil)
        error! (atom nil)]
    (get-invite-token persona-id send-token! #(reset! error! %))
    (fn []
      (if @peer!
        [peer-item persona-id peers! @peer! adding-peer?!]
        [:div.peer-item 
         [:div.peer-img-box
          [:img.peer-img 
           {:src "../img/user.png"}]]
         [:div.peer-info
          [:table.peer-info
           [:tbody
            [:tr
             [:td [:div.no-wrap "Give this token to your peer:"]]
             [:td {:width "100%"}
              [:input.input-text
               {:type "text"
                :disabled true
                :value @send-token!}]]]
            [:tr
             [:td [:div.no-wrap "Enter token from peer:"]]
             [:td
              [:input.input-text
               {:type "text"
                :value @receive-token!
                :on-change #(reset! receive-token! (-> % .-target .-value))}]]]
            [:tr
             [:td 
              [:button {:on-click 
                        (fn [] 
                          (model/token-to-peer 
                           @receive-token! 
                           #(reset! peer! %) 
                           #(reset! error! %)))} 
               "Add"]]
             [:td
              [error-control error!]]]]]]]))))

(defn peer-list [persona-id peers! adding-peer?!]
  (when @peers!
    [:div.peers 
     (when @adding-peer?! [add-peer-item persona-id peers! adding-peer?!])
     (doall (for [peer (:results @peers!)]
              ^{:key peer} [peer-item persona-id peers! peer]))]))

(defn peer-instructions []
  [:div.feed-item
   [:div
    [:img.nola-img {:src "img/nola.png"}]
    [:div.item-name  "Snap! Your have no peers!"]
    [:div
     [:ul.instruction-items
      [:li "Add peers by clicking the add peer icon (" [:img.header-icon {:src  "../img/add-peer.png"}] ") on the top right"]
      [:li "Browse help by clicking the help icon (" [:img.header-icon {:src  "../img/help.png"}] ") on the top right"]]]]])


(defn peer-page [peers! personas! persona! on-persona-select query! on-search!]
  (let [adding-peer?! (atom false)]
    (fn []
    (let [personas (into {} (map #(vector (:id %) %) (:items @personas!)))]
      [:div.settings-page
       [search/search-header
        :peers
        personas!
        persona!
        on-persona-select
        query!
        on-search!
        (partial header-actions adding-peer?!)]
       [error-control (state/error!)]
       [:h2 "Peers of " (:name (personas @persona!))] 
       [peer-list @persona! peers! adding-peer?!]
       (when (and (not (= @peers! {})) (not @adding-peer?!) (empty? (:results @peers!)))
         [peer-instructions])]))))

