(ns opencola.web-ui.view.peer 
  (:require
   [clojure.string :as string]
   [reagent.core :as reagent :refer [atom]]
   [opencola.web-ui.common :refer [to-boolean]]
   [opencola.web-ui.view.common :refer [action-img nbsp image-divider input-text input-checkbox]]
   [opencola.web-ui.model.peer :as model]
   [opencola.web-ui.view.search :as search]
   [opencola.web-ui.model.error :as error]
   [opencola.web-ui.location :as location]
   [alphabase.base58 :as b58]
   [cljs.pprint :as pprint]))


;; These methods are not really view. They are more binders.
(defn get-peers [persona-id peers!]
  (model/get-peers
   persona-id
   #(reset! peers! %)
   #(error/set-error! peers! %)))

(defn update-peer [persona-id peers! peer!]
  (model/update-peer
   persona-id
   @peer!
   #(reset! peers! %)
   #(error/set-error! peer! %)))

(defn delete-peer [persona-id peers! peer!]
  (model/delete-peer
   persona-id
   @peer!
   #(reset! peers! %)
   #(error/set-error! peer! %)))

(defn header-actions [adding-peer?!]
  [:div.header-actions 
   [:img.header-icon {:src  "../img/add-peer.png" :on-click #(swap! adding-peer?! not)}]
   image-divider
   [:img.header-icon {:src  "../img/feed.png" :on-click #(location/set-page! :feed)}]])

(defn map-to-token [m]
  (->> m (map (fn [[k v]] (str (name k) "=" v))) (interpose \|) (apply str)))

(defn peer-to-token [peer]
  (if (:id peer)
    (map-to-token (dissoc peer :token))))

(defn token-to-peer [token]
  (let [parts (string/split token "|")
        pairs (map #(string/split % #"\=" 2) parts)
        kvs (map (fn [[k v]] [(keyword k) (if (= k "isActive") (to-boolean v) v)]) pairs)]
    (into {:isActive false} kvs)))

(defn peer-item [persona-id peers! peer adding-peer?!]
  (let [creating? adding-peer?! ;; TODO - looks like not needed
        editing?! (atom creating?)
        p! (atom peer)]
    (fn []
      (let [image-uri (:imageUri @p!)]
        [:div.peer-item
         [:div.peer-img-box
          [:img.peer-img 
           {:src (if (not (empty? image-uri)) image-uri "../img/user.png")}]]
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
            [error/error-control @p!]
            [:button
             {:disabled (and (not adding-peer?!) (= @p! peer)) 
              :on-click #(do
                           (update-peer persona-id peers! p!)
                           (if adding-peer?! (reset! adding-peer?! false)))} "Save"] " "
            [:button {:on-click  #(do
                                    (reset! p! peer)
                                    (if adding-peer?! (reset! adding-peer?! false))
                                    (reset! editing?! false))} "Cancel"] " "
            (if (not creating?)
              [:button.delete-button {:on-click #(delete-peer persona-id peers! p!)} "Delete"])]
           [:div.edit-peer
            [:button {:on-click #(reset! editing?! true)} "Edit"]])]))))

(defn get-invite-token [persona-id token! add-error!]
  (model/get-invite-token
   persona-id
   #(reset! token! %)
   #(error/set-error! add-error! %)))

(defn add-peer-item [persona-id peers! adding-peer?!]
  (let [send-token! (atom "Loading...")
        receive-token! (atom "")
        peer! (atom nil)
        add-error! (atom {})]
    (get-invite-token persona-id send-token! add-error!)
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
                           #(error/set-error! add-error! %)))} 
               "Add"]]
             [:td
              [error/error-control @add-error!]]]]]]]))))

(defn peer-list [persona-id peers! adding-peer?!]
  (if @peers!
    [:div.peers 
     (if @adding-peer?! [add-peer-item persona-id peers! adding-peer?!])
     
     (doall (for [peer (:results @peers!)]
              ^{:key peer} [peer-item persona-id peers! peer]))]))

(defn peer-page [peers! personas! persona! on-persona-select query! on-search!]
  (let [adding-peer?! (atom false)]
    (fn []
      [:div.settings-page
       [search/search-header 
        personas! 
        persona! 
        on-persona-select 
        query! 
        on-search! 
        (partial header-actions adding-peer?!)]
       [error/error-control @peers!]
       [peer-list @persona! peers! adding-peer?!]])))

