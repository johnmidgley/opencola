(ns opencola.web-ui.view.persona
  (:require
   [reagent.core :as reagent :refer [atom]]
   [opencola.web-ui.model.persona :as model]
   [opencola.web-ui.view.common :refer [action-img input-text input-checkbox]]
   [opencola.web-ui.view.search :as search]
   [opencola.web-ui.model.error :as error]))

(defn init-personas [personas! on-success on-error]
  (model/get-personas
   (fn [personas] 
     (reset! personas! personas)
     (on-success))
   #(on-error %)))

(defn get-personas [personas!]
  (model/get-personas
   #(reset! personas! %)
   #(error/set-error! personas! %)))

(defn update-persona [personas! persona!]
  (model/update-persona
   @persona!
   #(reset! personas! %)
   #(error/set-error! persona! %)))

(defn delete-persona [personas! persona!]
  (model/delete-persona
   @persona!
   #(reset! personas! %)
   #(error/set-error! persona! %)))

(defn header-actions [adding-persona?!]
  [:div.header-actions 
   [:img.header-icon {:src  "../img/add-peer.png" :on-click #(swap! adding-persona?! not)}]])

(defn persona-item [personas! persona adding-persona?!]
  (let [creating? adding-persona?! ;; TODO - looks like not needed
        editing?! (atom creating?)
        p! (atom persona)]
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
             {:disabled (and (not adding-persona?!) (= @p! persona))
              :on-click #(do
                           (update-persona personas! p!)
                           (if adding-persona?! (reset! adding-persona?! false)))} "Save"] " "
            [:button {:on-click  #(do
                                    (reset! p! persona)
                                    (if adding-persona?! (reset! adding-persona?! false))
                                    (reset! editing?! false))} "Cancel"] " "
            (if (not creating?)
              [:button.delete-button {:on-click #(delete-persona personas! p!)} "Delete"])]
           [:div.edit-peer
            [:button {:on-click #(reset! editing?! true)} "Edit"]])]))))

(defn persona-list [personas! adding-persona?!]
  (if @personas!
    [:div.peers 
     #_(if @adding-persona?! [add-persona-item personas! adding-persona?!])
     (doall (for [persona @personas!]
              ^{:key persona} [persona-item personas! persona]))]))


(defn personas-page [personas! persona! on-persona-select query! on-search!]
  (let [adding-persona?! (atom false)]
    (fn []
      [:div.settings-page
       [search/search-header 
        personas! 
        persona! 
        on-persona-select 
        query! 
        on-search! 
        (partial header-actions adding-persona?!)]
       #_[error/error-control @peers!] ; make work
       [persona-list personas! adding-persona?!]])))
