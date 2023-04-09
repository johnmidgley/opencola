(ns opencola.web-ui.view.feed
  (:require [cljs.reader :as reader]
            [clojure.string :as string]
            [lambdaisland.uri :refer [uri]]
            [opencola.web-ui.common :refer [toggle-atom]]
            [opencola.web-ui.location :as location]
            [opencola.web-ui.model.error :as error]
            [opencola.web-ui.model.feed :as model]
            [opencola.web-ui.time :refer [format-time]]
            [opencola.web-ui.view.attachments :refer [attachment-control
                                                      item-attachments]]
            [opencola.web-ui.view.common :refer [action-img image-divider
                                                 inline-divider md->component
                                                 simple-mde]]
            [opencola.web-ui.view.search :as search]
            [opencola.web-ui.view.tags :refer [item-tags item-tags-summary]]
            [reagent.core :as reagent :refer [atom]]))


;; TODO: Look at https://github.com/Day8/re-com

(defn get-feed [persona-id query feed!]
  (model/get-feed
   nil
   persona-id
   query
   #(reset! feed! %)
   #(error/set-error! feed! %)))

(defn authority-actions-of-type [authority-id type item]
  (filter #(= authority-id (:authorityId %)) (-> item :activities type)))

(defn tags-as-string [authority-id item]
  (string/join " " (map :value (authority-actions-of-type authority-id :tag item))))


(defn update-feed-item [feed! view-item]
  (let [entity-id (:entityId view-item)
        updated-feed (update-in
                      @feed!
                      [:results]
                      #(map (fn [i] (if (= entity-id (:entityId i)) view-item i)) %))]
    (reset! feed! updated-feed)))

(defn set-item-error [feed! item error]
  (update-feed-item feed! (error/set-error item error)))

;; TODO - Use https://clj-commons.org/camel-snake-kebab/
;; Be careful with like
(defn edit-item
  ([]
   {:entityId ""
    :name ""
    :imageUri ""
    :description ""
    :like nil
    :tags ""
    :comment ""})
  ([authority-id item]
   (let [summary (:summary item)
         likes (-> item :activities :like)]
     {:entityId (:entityId item)
      :name (:name summary)
      :imageUri (:imageUri summary)
      :description (:description summary)
      :like (some #(when (= authority-id (:authorityId %)) (reader/read-string (:value %))) likes)
      :tags (tags-as-string authority-id item)
      :comment ""})))

(defn data-url [host data-id]
  (when data-id
    (str "/data/" data-id)))

(defn edit-control [editing?!]
  [:span.edit-entity {:on-click #(reset! editing?! true)} (action-img "edit")])

(defn get-item [feed entity-id]
  (->> feed :results (some #(when (= entity-id (:entityId %)) %))))


(defn remove-comment [item comment-id]
  (update-in item
             [:activities :comment]
             (fn [comments]
               (filterv #(not= comment-id (:id %)) comments))))


(defn update-comment [persona-id feed! entity-id comment-id text editing?! error!]
  (model/update-comment
   (:context @feed!)
   persona-id
   entity-id
   comment-id
   text
   #(update-feed-item feed! %)
   #(error/set-error! error! %)))

(defn delete-comment [feed! persona-id entity-id comment-id error!]
  (let [item (get-item @feed! entity-id)]
    (model/delete-comment
     (:context @feed!)
     persona-id
     comment-id
     ;; TODO: Pass in error, instead of adding to item?
     #(update-feed-item feed! (remove-comment (error/clear-error item) comment-id))
     #(error/set-error! error! %))))

(defn comment-control [persona-id! feed! entity-id comment-id text expanded?!]
  (let [text! (atom text)
        error! (atom {})]
    (fn []
      (when @expanded?!
        [:div.item-comment
         [:div.item-comment-edit
          [:textarea.comment-text-edit {:type "text"
                                        :value @text!
                                        :on-change #(reset! text! (-> % .-target .-value))}]
          [error/error-control @error!]
          [:button {:on-click #(update-comment @persona-id! feed! entity-id comment-id @text! expanded?! error!)}
           "Save"] " "
          [:button {:on-click #(reset! expanded?! false)} "Cancel"]
          (when comment-id
            [:button.delete-button
             {:on-click #(delete-comment feed! @persona-id! entity-id comment-id error!)} "Delete"])]]))))

(defn item-comment [persona-id! feed! entity-id comment-action]
  (let [editing?! (atom false)]
    (fn []
      (let [{authority-id :authorityId
             authority-name :authorityName
             epoch-second :epochSecond
             text :value
             comment-id :id} comment-action
            editable? (= authority-id @persona-id!)]
        (when (not editable?)
          (reset! editing?! false))
        [:div.item-comment
         [:div.item-attribution
          authority-name " " (format-time epoch-second) " "
          (when editable?
            [:span {:on-click #(reset! editing?! true)} [action-img "edit"]])
          ":"]
         (if (and editable? @editing?!)
           [comment-control persona-id! feed! entity-id comment-id text editing?!]
           [:div.item-comment-container [md->component {:class "item-comment-text"} text]])]))))

(defn item-comments [persona-id! preview-fn? expanded?! comment-actions feed! entity-id]
  (let [preview? (preview-fn?)
        more (- (count comment-actions) 3)
        comment-actions (if preview? (take 3 comment-actions) comment-actions)]
    (when (or @expanded?! (and preview? (not-empty comment-actions)))
      [:div.item-comments
       [:span {:on-click (fn [] (swap! expanded?! #(not %)))} "Comments:"]
       (doall (for [comment-action comment-actions]
                ^{:key comment-action} [item-comment persona-id! feed! entity-id comment-action]))
       [:div.item-comments-footer {:on-click (fn [] (swap! expanded?! #(not %)))}
        (when (> more 0)
          (if preview?
            [:span.action "Show more" (action-img "show")]
            [:span.action "Show less" (action-img "hide")]))]])))

(defn item-save [save-action]
  (let [{authority-name :authorityName
         epoch-second :epochSecond
         data-id :id
         host :host} save-action]
    [:tr.item-attribution
     [:td authority-name " "]
     [:td (format-time epoch-second)]
     [:td
      (when data-id
        [:span
         [:a.action-link  {:href (data-url host data-id) :target "_blank"} [action-img "archive"]]])]]))

(defn item-saves [expanded?! save-actions]
  (when @expanded?!
    [:div.item-saves
     [:div.list-header "Saves:"]
     [:table
      [:tbody
       (doall (for [save-action save-actions]
                ^{:key save-action} [item-save save-action]))]]]))

(defn item-like [like-action]
  (let [{authority-name :authorityName
         epoch-second :epochSecond} like-action]
    [:tr.item-attribution
     [:td (str authority-name)]
     [:td (format-time epoch-second)]]))

;; TODO: Templatize this - same for saves and comments
(defn item-likes [expanded?! like-actions]
  (when @expanded?!
    [:div.item-likes
     [:div.list-header "Likes:"]
     [:table
      [:tbody
       (doall (for [like-action like-actions]
                ^{:key like-action} [item-like like-action]))]]]))

(defn action-summary [persona-id! feed! key action-expanded? activities on-click]
  (let [actions (key activities)
        expanded? (key action-expanded?)
        highlight (some #(= @persona-id! (:authorityId %)) actions)]
    [:span
     [:span {:class (when highlight "highlight") :on-click on-click} (action-img (name key))]
     [:span {:on-click #(toggle-atom (map second action-expanded?) expanded?)} " " (count actions)
      (action-img (if @expanded? "hide" "show"))]]))

(defn save-item [persona-id feed! item]
  (let [actions (-> item :activities :save)
        saved? (some #(= persona-id (:authorityId %)) actions)]
    (when (not saved?)
      (model/save-entity
       (:context @feed!)
       persona-id
       item
       #(update-feed-item feed! %)
       #(update-feed-item feed! (error/set-error item %))))))

(defn update-display-entity [persona-id feed! edit-item item]
  (model/update-entity
   (:context @feed!)
   persona-id
   edit-item
   #(update-feed-item feed! %)
   #(update-feed-item feed! (error/set-error item %))))

(defn update-edit-entity [persona-id feed! editing?! edit-item! item]
  (model/update-entity
   (:context @feed!)
   persona-id
   @edit-item!
   #(update-feed-item feed! %)
   #(error/set-error! edit-item! %)))

(defn like-item [persona-id feed! item]
  (let [edit-item (edit-item persona-id item)]
    (update-display-entity persona-id feed! (update-in edit-item [:like] #(if % nil true)) item)))

;; TODO - Combing with tags-edit-control?
(defn tags-control [persona-id! feed! item tagging?!]
  (let [edit-item!  (atom (edit-item @persona-id! item))]
    (fn []
      (when @tagging?!
        [:div.tags-edit-control
         [:div.field-header "Tags:"]
         [:input.tags-text
          {:type "text"
           :value (:tags @edit-item!)
           :on-change #(swap! edit-item! assoc-in [:tags] (-> % .-target .-value))}]
         [error/error-control @edit-item!]
         [:button {:on-click #(update-edit-entity @persona-id! feed! tagging?! edit-item! item)} "Save"] " "
         [:button {:on-click #(reset! tagging?! false)} "Cancel"] " "]))))

(defn persona-select [personas! persona-id!]
  [:select {:id "persona-select"
            :on-change #(reset! persona-id! (-> % .-target .-value))
            :value @persona-id!}
   (doall (for [persona (:items @personas!)]
            ^{:key persona} [:option  {:value (:id persona)} (:name persona)]))])

(defn item-activities [persona-id! personas! feed! item editing?!]
  (let [action-expanded? (apply hash-map (mapcat #(vector % (atom false)) [:save :like :tag :comment :attach]))
        tagging? (atom false)
        commenting? (atom false)
        attaching? (atom false)
        preview-fn? (fn [] (every? #(not @%) (map second action-expanded?)))]
    (fn []
      (let [entity-id (:entityId item)
            activities (:activities item)]
        [:div.activities-summary
         (when personas!
           [:span [persona-select personas! persona-id!] inline-divider])
         [action-summary persona-id! feed! :save action-expanded? activities #(save-item @persona-id! feed! item)]
         inline-divider
         [action-summary persona-id! feed! :like action-expanded? activities #(like-item @persona-id! feed! item)]
         inline-divider
         [action-summary persona-id! feed! :tag action-expanded?  activities #(swap! tagging? not)]
         inline-divider
         [action-summary persona-id! feed! :comment action-expanded? activities #(swap! commenting? not)]
         inline-divider
         [action-summary persona-id! feed! :attach action-expanded? activities #(swap! attaching? not)]
         inline-divider
         [edit-control editing?!]
         [:div.activity-block
          [tags-control persona-id! feed! item tagging?]
          [comment-control persona-id! feed! entity-id nil "" commenting?]
          ;; TODO: Cleanup error handling + make update-feed-item general
          [attachment-control persona-id! feed! entity-id attaching? update-feed-item #(set-item-error feed! item %)]
          [item-saves (:save action-expanded?) (:save activities)]
          [item-likes (:like action-expanded?) (:like activities)]
          [item-tags (:tag action-expanded?) (:tag activities)]
          [item-comments persona-id! preview-fn? (:comment action-expanded?) (:comment activities) feed! entity-id]
          [item-attachments (:attach action-expanded?) (:attach activities)]]]))))


(defn item-name [summary]
  (let [item-uri (:uri summary)
        host (:host (uri item-uri))
        name (or (:name summary) (:hostName summary))]
    (when (seq name)
      (if (empty? item-uri)
        [:div.item-name name]
        [:div.item-name
         [:a.item-link {:href (str item-uri) :target "_blank"} name]
         [:div.item-host host]]))))

(defn item-image [summary]
  (let [item-uri (:uri summary)
        image-uri (or (:imageUri summary) (and (not item-uri) (:postedByImageUri summary)))
        img [:img.item-img {:src image-uri}]]
    (when image-uri
      [:div.item-img-box
       (if (empty? item-uri)
         img
         [:a {:href item-uri :target "_blank"} img])])))

(defn display-feed-item [persona-id! personas! feed! item editing?!]
  (let [summary (:summary item)]
    (fn []
      [:div.feed-item
       [item-name summary]
       [:div.item-body
        [item-image summary]
        [md->component {:class "item-desc"}  (:description summary)]]
       [item-tags-summary (-> item :activities :tag)]
       [:div.posted-by "Posted by: " (:postedBy summary)]
       [item-activities persona-id! personas! feed! item editing?!]
       [error/error-control item]])))

(defn name-edit-control [edit-item!]
  [:div.item-name
   [:div.field-header "Title:"]
   [:input.item-link
    {:type "text"
     :value (:name @edit-item!)
     :on-change #(swap! edit-item! assoc-in [:name] (-> % .-target .-value))}]])

(defn image-uri-edit-control [edit-item!]
  (let [image-uri (:imageUri @edit-item!)]
    [:div.item-uri-edit-control
     [:div.item-img-box
      (when (seq image-uri)
        [:img.item-img {:src image-uri}])]
     [:div.item-image-url
      [:div.field-header "Image URL:"]
      [:input.item-img-url
       {:type "text"
        :value (:imageUri @edit-item!)
        :on-change #(swap! edit-item! assoc-in [:imageUri] (-> % .-target .-value))}]]]))


(defn tags-edit-control [edit-item!]
  [:div.tags-edit-control
   [:div.field-header "Tags:"]
   [:input.tags-text
    {:type "text"
     :value (:tags @edit-item!)
     :on-change #(swap! edit-item! assoc-in [:tags] (-> % .-target .-value))}]])

(defn description-edit-control [edit-item! state!]
  [:div.description-edit-control
   [:div.field-header "Description:"]
   [:div.item-desc
    [simple-mde (str (:entityId @edit-item!) "-desc") (:description @edit-item!) state!]]])

(defn comment-edit-control [edit-item!]
  [:div.comment-edit-control
   [:div.field-header "Comment:"]
   [:div
    [:textarea.comment-text-edit
     {:type "text"
      :value (:comment @edit-item!)
      :on-change #(swap! edit-item! assoc-in [:comment] (-> % .-target .-value))}]]])

(defn like-edit-control [edit-item!]
  [:div.like-edit-control
   [:span.field-header "Like: "]
   [:span {:class (when (:like @edit-item!) "highlight")
           :on-click (fn [] (swap! edit-item! update-in [:like] #(if % nil true)))}
    (action-img "like")]])

;; TODO: Put error in separate variable - then create and manage edit-iten only in here
(defn edit-item-control [personas! persona-id! item edit-item! on-save on-cancel on-delete]
  (let [description-state! (atom nil)]
    (fn []
      (let [deletable? (some #(= @persona-id! (:authorityId %)) (-> item :activities :save))]
        [:div.feed-item
         [:div.error (:error @edit-item!)]
         [name-edit-control edit-item!]
         [image-uri-edit-control edit-item!]
         [description-edit-control edit-item! description-state!]
         [like-edit-control edit-item!]
         [tags-edit-control edit-item!]
         [comment-edit-control edit-item!]
         [error/error-control @edit-item!]
         (when personas!
           [:span [persona-select personas! persona-id!] " "])
         [:button {:on-click (fn []
                               (swap! edit-item! assoc-in [:description] (.value @description-state!))
                               (on-save @persona-id!))} "Save"] " "
         [:button {:on-click on-cancel} "Cancel"] " "
         (when deletable?
           [:button.delete-button {:on-click on-delete} "Delete"])]))))


(defn delete-feed-item [feed! entity-id]
  (swap! feed! update-in [:results] (fn [results] (remove #(= (:entityId %) entity-id) results))))


(defn delete-entity [persona-id feed! editing?! item edit-item!]
  (let [entity-id (:entityId item)]
    (model/delete-entity
     (:context @feed!)
     persona-id
     entity-id
     (fn [item]
       (if (empty? item) ;; Item may not be deletable, if you don't own it. Should hide it
         (delete-feed-item feed! entity-id)
         (update-feed-item feed! item))
       (when editing?! (reset! editing?! false)))
     #(error/set-error! edit-item! %))))


;; TODO: Use keys to get 
(defn edit-feed-item [personas! persona-id! feed! item editing?!]
  (let [edit-item! (atom (edit-item @persona-id! item))]
    (edit-item-control
     personas!
     persona-id!
     item
     edit-item!
     #(update-edit-entity @persona-id! feed! editing?! edit-item! item)
     #(reset! editing?! false)
     #(delete-entity @persona-id! feed! editing?! item edit-item!))))


(defn feed-item [persona-id personas! feed! item]
  (let [editing?! (atom false)
        persona-id! (atom (or persona-id (:personaId item)))]
    (fn []
      (if @editing?!
        [edit-feed-item personas! persona-id! feed! item editing?!]
        [display-feed-item persona-id! personas! feed! item editing?!]))))


(defn feed-status [feed!]
  [:div.feed-status
   (let [query (:query @feed!)]
     (when (not-empty query)
       (str (count (:results @feed!)) " results for '" query "'")))])

(defn feed-list [persona-id! personas! feed!]
  (when @feed!
    [:div.feed
     [feed-status feed!]
     (doall (for [item  (:results @feed!)]
              ^{:key [item @persona-id!]}
              [feed-item @persona-id! (when (not @persona-id!) personas!) feed! item]))]))

(defn header-actions [persona! creating-post?!]
  [:div.header-actions
   [:img.header-icon {:src  "../img/new-post.png" :on-click #(swap! creating-post?! not)}]
   (when true ;@persona!
     [:span
      image-divider
      [:img.header-icon {:src  "../img/peers.png" :on-click #(location/set-page! :peers)}]])])


(defn prepend-feed-item [feed! view-item]
  (swap! feed! update-in [:results] #(into [view-item] %)))

(defn new-post [persona-id feed! creating-post!? edit-item!]
  (model/new-post
   (:context @feed!)
   persona-id
   @edit-item!
   #(do
      (prepend-feed-item feed! %)
      (reset! creating-post!? false))
   #(error/set-error! edit-item! %)))

;; TODO: Make parameter ordering consistent. Some places have persona-id then personas, others
;; are the other way around.
;; TODO: Only pass atoms when necessary. There are some cases where the personas! is passed, when @personas! 
;; would suffice
(defn feed-page [feed! personas! persona-id! on-persona-select query! on-search]
  (let [creating-post?! (atom false)]
    (fn []
      [:div#opencola.feed-page
       [search/search-header
        :feed
        personas!
        persona-id!
        on-persona-select
        query!
        on-search
        (partial header-actions persona-id! creating-post?!)]
       [error/error-control @feed!]
       (when @creating-post?!
         (let [edit-item! (atom (edit-item))]
           [edit-item-control
            (when (not @persona-id!) personas!)
            (atom (or @persona-id! (-> @personas! :items first :id)))
            nil
            edit-item!
            #(new-post % feed! creating-post?! edit-item!)
            #(reset! creating-post?! false) nil]))
       [feed-list persona-id! personas! feed!]])))
