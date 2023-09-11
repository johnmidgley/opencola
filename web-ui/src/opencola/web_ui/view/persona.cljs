(ns opencola.web-ui.view.persona
  (:require
   [reagent.core :as reagent :refer [atom]]
   [opencola.web-ui.app-state :as state]
   [opencola.web-ui.model.persona :as model]
   [opencola.web-ui.view.common :refer [action-img input-text input-checkbox error-control]]
   [opencola.web-ui.view.search :as search]))

(defn init-personas [personas! on-success on-error]
  (model/get-personas
   (fn [personas] 
     (reset! personas! personas)
     (on-success))
   #(on-error %)))

(defn create-persona [personas! persona! on-success on-error]
  (model/create-persona
   (dissoc @persona! :error)                          ; Switch to non-atom
   #(do 
      (reset! personas! %)
      (on-success))
   #(on-error %)))

(defn get-personas [personas! on-error]
  (model/get-personas
   #(reset! personas! %)
   #(on-error %)))

(defn update-persona [personas! persona! on-error]
  (model/update-persona
   @persona!
   #(reset! personas! %)
   #(on-error %)))

(defn delete-persona [personas! persona! on-error]
  (model/delete-persona
   @persona!
   #(reset! personas! %)
   #(on-error %)))

(defn persona-select [personas! persona-id!]
  [:select {:id "persona-select"
            :on-change #(reset! persona-id! (-> % .-target .-value))
            :value @persona-id!}
   (doall (for [persona (:items @personas!)]
            ^{:key persona} [:option  {:value (:id persona)} (:name persona)]))])

(defn header-actions [adding-persona?!]
  [:div.header-actions 
   [:img.header-icon {:src  "../img/add-peer.png" :on-click #(swap! adding-persona?! not)}]])

(defn persona-item [personas! persona]
  (let [editing?! (atom false)
        p! (atom persona)
        error! (atom nil)
        on-error #(reset! error! %)]
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
             [:td [:span.id [input-text p! :id]]]]
            [:tr 
             [:td.peer-field [action-img "key"]] 
             [:td [:span.key [input-text p! :publicKey]]]]
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
             {:disabled (= @p! persona)
              :on-click (fn [] (update-persona personas! p! on-error))}
             "Save"] " "
            [:button {:on-click  #(do
                                    (reset! p! persona)
                                    (reset! editing?! false))} "Cancel"] " "
            [:button.delete-button {:on-click #(delete-persona personas! p! on-error)} "Delete"]]
           [:div.edit-peer
            [:button {:on-click #(reset! editing?! true)} "Edit"]])]))))

(def empty-persona
  {:id ""  
   :name "" 
   :publicKey "" 
   :address "ocr://relay.opencola.net" 
   :imageUri "" 
   :isActive true})

(defn add-persona-item [personas! adding-persona?!]
  (let [persona! (atom empty-persona)
        error! (atom nil)]
    (fn []
      (let [image-uri (:imageUri @persona!)]
        [:div.peer-item
         [:div.peer-img-box
          [:img.peer-img 
           {:src (if (seq image-uri) image-uri "../img/user.png")}]]
         [:div.peer-info
          [:table.peer-info
           [:tbody
            [:tr 
             [:td.peer-field [action-img "user"]] 
             [:td [input-text persona! :name  true]]]
            [:tr 
             [:td.peer-field [action-img "link"]] 
             [:td [:span.uri [input-text persona! :address true]]]]
            [:tr 
             [:td.peer-field [action-img "photo"]] 
             [:td [:span.uri [input-text persona! :imageUri true]]]]
            [:tr 
             [:td.peer-field [action-img "refresh"]] 
             [:td [input-checkbox persona! :isActive true]]]]]]
         [:div
          [error-control error!]
          [:button
           {:disabled (= empty-persona @persona!)
            :on-click (fn [] (create-persona personas! persona! #(reset! adding-persona?! false) #(reset! error! %)))} 
           "Save"] " "
          [:button {:on-click  #(reset! adding-persona?! false)} "Cancel"] " "]]))))

(defn persona-list [personas! adding-persona?!]
  (when @personas!
    [:div.peers 
     (when @adding-persona?! [add-persona-item personas! adding-persona?!])
     (doall (for [persona (:items @personas!)]
              ^{:key persona} [persona-item personas! persona]))]))


(defn personas-page [personas! persona! on-persona-select query! on-search!]
  (let [adding-persona?! (atom false)]
    (fn []
      [:div.settings-page
       [search/search-header 
        :personas
        personas! 
        persona! 
        on-persona-select 
        query! 
        on-search! 
        (partial header-actions adding-persona?!)]
       [error-control (state/error!)] 
       [:h2 "Personas"]
       [persona-list  personas! adding-persona?!]])))
