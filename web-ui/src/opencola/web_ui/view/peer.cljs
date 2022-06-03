(ns opencola.web-ui.view.peer 
  (:require
   [clojure.string :as string]
   [reagent.core :as reagent :refer [atom]]
   [opencola.web-ui.common :as common :refer [action-img]]
   [opencola.web-ui.model.peer :as model]
   [opencola.web-ui.view.search :as search]
   [opencola.web-ui.model.error :as error]
   [alphabase.base58 :as b58]
   [cljs.pprint :as pprint]))

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

(defn update-token [peer!]
  (swap! peer! assoc-in [:token] (peer-to-token @peer!)))

(defn peer-value [peer! key editing?]
  [:div.peer-value
   [:input.peer-value
    {:type "text"
     :disabled (not editing?)
     :value (key @peer!)
     :on-change #(do
                   (swap! peer! assoc-in [key] (-> % .-target .-value))
                   (update-token peer!))}]])

(defn peer-active [peer! editing?]
  [:input
   {:type "checkbox"
    :disabled (not editing?)
    :checked (:isActive @peer!)
    :on-change #(do
                  (swap! peer! assoc-in [:isActive] (-> % .-target .-checked))
                  (update-token peer!))}])


(defn peer-token [peer! editing?]
  [:div.peer-value
   [:input.peer-value
    {:type "text"
     :disabled (not editing?)
     :value (:token @peer!)
     :on-change #(let [token  (-> % .-target .-value)
                       peer (assoc-in (token-to-peer token) [:token] token)]
                   (reset! peer! peer))}]])

(defn peer-item [peers! peer adding-peer?!]
  (let [creating? adding-peer?!
        editing?! (atom creating?)
        original-peer (assoc-in peer [:token] (peer-to-token peer)) 
        p! (atom original-peer)]
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
             [:td [peer-active p! @editing?!]]]
            [:tr 
             [:td.peer-field [action-img "token"]] 
             [:td [peer-token p! creating?]]]]]]
         (if @editing?!
           [:div
            [:button {:on-click #(do
                                   (model/update-peer peers! (dissoc @p! :token))
                                   (if adding-peer?! (reset! adding-peer?! false)))} "Save"] " "
            [:button {:on-click  #(do
                                    (reset! p! original-peer)
                                    (if adding-peer?! (reset! adding-peer?! false))
                                    (reset! editing?! false))} "Cancel"] " "
            (if (not creating?)
              [:button.delete-button {:on-click #(model/delete-peer peers! peer)} "Delete"])]
           [:div.edit-peer
            [:button {:on-click #(reset! editing?! true)} "Edit"]])]))))


(defn peer-list [peers! adding-peer?!]
  (if @peers!
    [:div.peers 
     (if @adding-peer?! [peer-item peers! {:isActive false} adding-peer?!])
     
     (doall (for [peer (:results @peers!)]
              ^{:key peer} [peer-item peers! peer]))]))

(defn peer-page [peers! query! on-search!]
  (let [adding-peer?! (atom false)]
    (fn []
      [:div.settings-page
       [search/search-header query! on-search! (partial header-actions query! adding-peer?!)]
       [common/error-message]
       [peer-list peers! adding-peer?!]])))

