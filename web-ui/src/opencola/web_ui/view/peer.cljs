(ns opencola.web-ui.view.peer 
  (:require 
   [reagent.core :as reagent :refer [atom]]
   [opencola.web-ui.app-state :as state]
   [opencola.web-ui.view.common :refer [input-checkbox error-control text-input-component button-component 
                                        edit-control-buttons empty-page-instructions swap-atom-data! profile-img]]
   [opencola.web-ui.model.peer :as model]
   [opencola.web-ui.view.search :as search]
   [opencola.web-ui.location :as location]))


;; These methods are not really view. They are more binders.
(defn get-peers [persona-id peers! on-error]
  (model/get-peers
   persona-id
   #(reset! peers! %)
   #(on-error %)))

(defn get-invite-token [persona-id token! on-error]
  (model/get-invite-token
   persona-id
   #(reset! token! %)
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

(defn map-to-token [m]
  (->> m (map (fn [[k v]] (str (name k) "=" v))) (interpose \|) (apply str)))

(defn peer-item [persona-id peers! peer adding-peer?!]
  (let [creating? adding-peer?! ;; TODO - looks like not needed
        editing?! (atom creating?)
        p! (atom peer)
        error! (atom nil)]
    (fn []
      (let [image-uri (:imageUri @p!)]
        [:div.list-item
         [profile-img image-uri (:name @p!) (:id @p!)]
         [:div.peer-info
          [text-input-component {:value (:name @p!) :disabled (not @editing?!) :icon-class "icon-persona" :name "peer-name"} #(swap-atom-data! % p! :name)]
          [text-input-component {:value (:id @p!) :disabled true :icon-class "icon-id" :name "peer-id"} #(swap-atom-data! % p! :id)]
          [text-input-component {:value (:publicKey @p!) :disabled (not @editing?!) :icon-class "icon-key" :name "peer-key"} #(swap-atom-data! % p! :publicKey)]
          [text-input-component {:value (:address @p!) :disabled (not @editing?!) :icon-class "icon-link" :name "peer-link"} #(swap-atom-data! % p! :address)]
          [text-input-component {:value (:imageUri @p!) :disabled (not @editing?!) :icon-class "icon-photo" :name "peer-img"} #(swap-atom-data! % p! :imageUri)]
          [input-checkbox {:checked (:isActive @p!) :disabled (not @editing?!) :icon-class "icon-refresh" :name "peer-active"} #(swap! p! assoc-in [:isActive] (-> % .-target .-checked))]]
         (if @editing?!
           [edit-control-buttons {:on-save (fn []
                                             (update-peer persona-id peers! p! #(do (println "ERROR") (reset! error! %)))
                                             (when adding-peer?! (reset! adding-peer?! false)))
                                  :save-disabled (and (not adding-peer?!) (= @p! peer))
                                  :on-cancel #(do
                                                (reset! p! peer)
                                                (when adding-peer?! (reset! adding-peer?! false))
                                                (reset! editing?! false))
                                  :on-delete (fn [] (delete-peer persona-id peers! p! #(reset! error! %)))} 
            (not creating?) error!]
           [button-component {:text "Edit" :class " edit-control-button edit-button"} #(reset! editing?! true)])]))))

(defn add-peer-item [persona-id peers! adding-peer?!]
  (let [send-token! (atom "Loading...")
        receive-token! (atom "")
        peer! (atom nil)
        error! (atom nil)]
    (get-invite-token persona-id send-token! #(reset! error! %))
    (fn []
      (if @peer!
        [peer-item persona-id peers! @peer! adding-peer?!]
        [:div.list-item 
         [profile-img "../img/svg/persona.svg" "New Peer"]
         [:div.peer-info
          [text-input-component {:value @send-token! :title "Your token: " :disabled true :class "no-wrap" :copy-button true :name "your-key"} #()]
          [text-input-component {:value @receive-token! :title "Thier token:" :class "no-wrap" :name "their-key"} #(reset! receive-token! (-> % .-target .-value))]
          [edit-control-buttons {:on-save (fn []
                                            (model/token-to-peer
                                             @receive-token!
                                             #(reset! peer! %)
                                             #(reset! error! %)))
                                 :on-cancel #(reset! adding-peer?! false)}
           false error!]]]))))

(defn peer-list [persona-id peers! adding-peer?!]
  (when @peers!
    [:div.content-list.peer-list 
     (when @adding-peer?! [add-peer-item persona-id peers! adding-peer?!])
     (doall (for [peer (:results @peers!)]
              ^{:key peer} [peer-item persona-id peers! peer]))]))


(defn peer-page [peers! personas! persona! on-persona-select query! on-search!]
  (let [adding-peer?! (atom false)]
    (fn []
    (let [personas (into {} (map #(vector (:id %) %) (:items @personas!)))]
      [:div.peers-page
       [search/search-header
        :peers
        personas!
        persona!
        on-persona-select
        query!
        on-search! 
        adding-peer?!]
       [error-control (state/error!)]
       [:h2.text-center  "Peers of " (:name (personas @persona!))] 
       [peer-list @persona! peers! adding-peer?!]
       (when (and (not (= @peers! {})) (not @adding-peer?!) (empty? (:results @peers!)))
         [empty-page-instructions :peers "This persona has no peers!"])]))))

