; Copyright 2024 OpenCola
;
; Licensed under the Apache License, Version 2.0 (the "License");
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
; 
;    http://www.apache.org/licenses/LICENSE-2.0
;
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.

(ns opencola.web-ui.view.peer 
  (:require 
   [reagent.core :as r]
   [opencola.web-ui.app-state :as state]
   [opencola.web-ui.view.common :refer [input-checkbox error-control text-input-component button-component 
                                        edit-control-buttons empty-page-instructions swap-atom-data! profile-img icon]]
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
        editing?! (r/atom creating?)
        show-advanced?! (r/atom false)
        p! (r/atom peer)
        error! (r/atom nil)]
    (fn []
      (let [image-uri (:imageUri @p!)]
        [:div.list-item
         [profile-img image-uri (:name @p!) (:id @p!)]
         [:div.peer-info
          [text-input-component 
           {:value (:name @p!) :disabled (not @editing?!) :icon-class "icon-persona" :name "peer-name" :icon-tool-tip-text "Name"} 
           #(swap-atom-data! % p! :name)]
          
          (when (or @editing?! @show-advanced?!)
            [:div.peer-info
             [text-input-component
              {:value (:id @p!) :disabled true :icon-class "icon-id" :name "peer-id" :icon-tool-tip-text "Id"}
              #(swap-atom-data! % p! :id)]])
          
          [text-input-component
           {:value (:address @p!) :disabled (not @editing?!) :icon-class "icon-link" :name "peer-link" :icon-tool-tip-text "Link"}
           #(swap-atom-data! % p! :address)]
          
          [text-input-component 
           {:value (:imageUri @p!) :disabled (not @editing?!) :icon-class "icon-photo" :name "peer-img" :icon-tool-tip-text "Profile Image"} 
           #(swap-atom-data! % p! :imageUri)] 
          
          [input-checkbox 
           {:checked (:isActive @p!) :disabled (not @editing?!) :icon-class "icon-refresh" :name "peer-active" :tool-tip-text "Sync"} 
           #(swap! p! assoc-in [:isActive] (-> % .-target .-checked))]]
         
         #_(when (not @editing?!)
           [button-component {:class "show-advanced-button" :text (str (if @show-advanced?! "Hide" "Show all"))} #(swap! show-advanced?! not)])
         
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
            error!]
           [button-component {:text "Edit" :class " edit-control-button edit-button"} #(reset! editing?! true)])]))))

(def token-instruction-text 
  "Give 'Your token' to your peer, and enter the token given by your peer into the 'Peer token' box. Then click save.")

(defn add-peer-item [persona-id peers! adding-peer?!]
  (let [send-token! (r/atom "Loading...")
        receive-token! (r/atom "")
        peer! (r/atom nil)
        error! (r/atom nil)]
    (get-invite-token persona-id send-token! #(reset! error! %))
    (fn []
      (if @peer!
        [peer-item persona-id peers! @peer! adding-peer?!]
        [:div.list-item 
         [profile-img nil ""]
         [:div.peer-info
          [text-input-component 
           send-token!
           {:title "Your token: " 
            :disabled true 
            :class "no-wrap"
            :name "your-key" 
            :copy-button-class "copy-token-button"}
           #()]
          [text-input-component 
           {:value @receive-token! 
            :title "Peer token:" 
            :class "no-wrap" 
            :name "their-key"} 
           #(reset! receive-token! (-> % .-target .-value))]
          [:div.add-peer-footer [edit-control-buttons {:on-save (fn []
                                                  (model/token-to-peer
                                                   @receive-token!
                                                   #(reset! peer! %)
                                                   #(reset! error! %)))
                                       :on-cancel #(reset! adding-peer?! false)}
                 error!]
           [icon {:icon-class "icon-help" :wrapper-class "help-indicator" :tool-tip-class "help-indicator-text" :tool-tip-text token-instruction-text}]]]]))))

(defn peer-list [persona-id peers! adding-peer?!]
  (when @peers!
    [:div.content-list.peer-list 
     (when @adding-peer?! [add-peer-item persona-id peers! adding-peer?!])
     (doall (for [peer (:results @peers!)]
              ^{:key peer} [peer-item persona-id peers! peer]))]))


(defn peer-page [peers! personas! persona! on-persona-select query! on-search!]
  (let [adding-peer?! (r/atom false)]
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

