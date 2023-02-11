(ns opencola.web-ui.view.peer 
  (:require
   [clojure.string :as string]
   [reagent.core :as reagent :refer [atom]]
   [opencola.web-ui.common :as common :refer [action-img nbsp]]
   [opencola.web-ui.model.peer :as model]
   [opencola.web-ui.view.search :as search]
   [opencola.web-ui.model.error :as error]
   [alphabase.base58 :as b58]
   [cljs.pprint :as pprint]))


(defn get-peers [peers!]
  (model/get-peers
   #(reset! peers! %)
   #(error/set-error! peers! %)))


(defn header-actions [query! adding-peer?!]
  [:div.header-actions 
   [:img.header-icon {:src  "../img/add-peer.png" :on-click #(swap! adding-peer?! not)}]
   common/image-divider
   [:img.header-icon {:src  "../img/feed.png" :on-click #(common/set-location (str "#/feed?q=" @query!))}]])

(defn map-to-token [m]
  (->> m (map (fn [[k v]] (str (name k) "=" v))) (interpose \|) (apply str)))

(defn peer-to-token [peer]
  (if (:id peer)
    (map-to-token (dissoc peer :token))))

(defn to-boolean [value]
  (if value
    (case (string/lower-case value)
      "true" true
      "false" false
      value)
    false))

(defn token-to-peer [token]
  (let [parts (string/split token "|")
        pairs (map #(string/split % #"\=" 2) parts)
        kvs (map (fn [[k v]] [(keyword k) (if (= k "isActive") (to-boolean v) v)]) pairs)]
    (into {:isActive false} kvs)))

(defn peer-value [peer! key editing?]
  [:div.peer-value
   [:input.peer-value
    {:type "text"
     :disabled (not editing?)
     :value (key @peer!)
     :on-change #(swap! peer! assoc-in [key] (-> % .-target .-value))}]])

(defn peer-active [peer! editing?]
  [:input
   {:type "checkbox"
    :disabled (not editing?)
    :checked (:isActive @peer!)
    :on-change #(swap! peer! assoc-in [:isActive] (-> % .-target .-checked))}])

(defn update-peer [peers! peer!]
  (model/update-peer
   @peer!
   #(reset! peers! %)
   #(error/set-error! peer! %)))

(defn delete-peer [peers! peer!]
  (model/delete-peer
   @peer!
   #(reset! peers! %)
   #(error/set-error! peer! %)))

(defn peer-item [peers! peer adding-peer?!]
  (let [creating? adding-peer?!
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
             [:td [peer-value p! :name @editing?!]]]
            [:tr 
             [:td.peer-field [action-img "id"]] 
             [:td [:span.id [peer-value p! :id creating?]]]]
            [:tr 
             [:td.peer-field [action-img "key"]] 
             [:td [:span.key [peer-value p! :publicKey @editing?!]]]]
            [:tr 
             [:td.peer-field [action-img "link"]] 
             [:td [:span.uri [peer-value p! :address @editing?!]]]]
            [:tr 
             [:td.peer-field [action-img "photo"]] 
             [:td [:span.uri [peer-value p! :imageUri @editing?!]]]]
            [:tr 
             [:td.peer-field [action-img "refresh"]] 
             [:td [peer-active p! @editing?!]]]]]]
         (if @editing?!
           [:div
            [error/error-control @p!]
            [:button
             {:disabled (and (not adding-peer?!) (= @p! peer)) 
              :on-click #(do
                           (update-peer peers! p!)
                           (if adding-peer?! (reset! adding-peer?! false)))} "Save"] " "
            [:button {:on-click  #(do
                                    (reset! p! peer)
                                    (if adding-peer?! (reset! adding-peer?! false))
                                    (reset! editing?! false))} "Cancel"] " "
            (if (not creating?)
              [:button.delete-button {:on-click #(delete-peer peers! p!)} "Delete"])]
           [:div.edit-peer
            [:button {:on-click #(reset! editing?! true)} "Edit"]])]))))

(defn get-invite-token [token! add-error!]
  (model/get-invite-token
   #(reset! token! %)
   #(error/set-error! add-error! %)))

(defn add-peer-item [peers! adding-peer?!]
  (let [send-token! (atom "Loading...")
        receive-token! (atom "")
        peer! (atom nil)
        add-error! (atom {})]
    (get-invite-token send-token! add-error!)
    (fn []
      (if @peer!
        [peer-item peers! @peer! adding-peer?!]
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
              [:input.peer-value
               {:type "text"
                :disabled true
                :value @send-token!}]]]
            [:tr
             [:td [:div.no-wrap "Enter token from peer:"]]
             [:td
              [:input.peer-value
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

(defn peer-list [peers! adding-peer?!]
  (if @peers!
    [:div.peers 
     (if @adding-peer?! [add-peer-item peers! adding-peer?!])
     
     (doall (for [peer (:results @peers!)]
              ^{:key peer} [peer-item peers! peer]))]))

(defn peer-page [peers! personas! query! on-search!]
  (let [adding-peer?! (atom false)]
    (fn []
      [:div.settings-page
       [search/search-header personas! query! on-search! (partial header-actions query! adding-peer?!)]
       [error/error-control @peers!]
       [peer-list peers! adding-peer?!]])))

