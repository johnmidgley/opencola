(ns opencola.web-ui.view.feed
  (:require [cljs.reader :as reader]
            [clojure.string :as string]
            [reagent.core :as r]
            [lambdaisland.uri :refer [uri]]
            [opencola.web-ui.app-state :as state]
            [opencola.web-ui.util :refer [distinct-by]]
            [opencola.web-ui.common :refer [toggle-atom]]
            [opencola.web-ui.location :as location]
            [opencola.web-ui.window :as window]
            [opencola.web-ui.model.feed :as model :refer [upload-files
                                                          upload-result-to-attachments]]
            [opencola.web-ui.model.like :refer [like-entity!]]
            [opencola.web-ui.model.tag :refer [tag-entity!]]
            [opencola.web-ui.view.attachments :refer [attachments-preview item-attachments distinct-attachments]]
            [opencola.web-ui.view.comments :refer [comment-control
                                                   comment-edit-control
                                                   item-comments]]
            [opencola.web-ui.view.common :refer [hidden-file-input upload-progress error-control button-component icon empty-page-instructions profile-img
                                                 md->component select-files-control simple-mde text-input button-component edit-control-buttons item-divider]]
            [opencola.web-ui.view.likes :refer [item-likes like-edit-control]]
            [opencola.web-ui.view.persona :refer [persona-select]]
            [opencola.web-ui.view.saves :refer [item-saves save-item]]
            [opencola.web-ui.view.search :as search]
            [opencola.web-ui.view.tags :refer [item-tags item-tags-summary
                                               item-tags-summary-from-string]]
            [reagent.core :as reagent :refer [atom]]))


;; TODO: Look at https://github.com/Day8/re-com

(defn retrieve-feed-items [persona-id context paging-token query on-success on-error] 
  (model/get-feed
   context
   paging-token
   persona-id
   query
   on-success
   #(on-error %)))

(defn get-feed [persona-id query feed! on-error]
  (retrieve-feed-items 
   persona-id
   (:context @feed!)
   nil
   query
   #(do (reset! feed! %)
        (window/scroll-to-top))
   on-error))

(def previous-feed-state! (r/atom nil))

(defn paginate-feed [persona-id query feed! on-error]
  (when (and (:pagingToken @feed!) (not @previous-feed-state!))
    (reset! previous-feed-state! @feed!)
    (retrieve-feed-items
     persona-id
     (:context @feed!)
     (:pagingToken @feed!)
     query
     #(do
        (when (identical? @previous-feed-state! @feed!)
         (reset! feed! (assoc % :results (concat (:results @feed!) (:results %)))))
        (reset! previous-feed-state! nil))
     on-error)))

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

(defn get-like-value [authority-id item]
  (let [likes (-> item :activities :like)]
    (some #(when (= authority-id (:authorityId %)) (reader/read-string (:value %))) likes)))

;; TODO - Use https://clj-commons.org/camel-snake-kebab/
;; Be careful with like
(defn edit-item
  ([]
   {:entityId ""
    :name ""
    :imageUri ""
    :description ""
    :like nil
    :attachments []
    :tags ""
    :comment ""})
  ([authority-id item]
   (let [summary (:summary item)]
     {:entityId (:entityId item)
      :name (:name summary)
      :imageUri (:imageUri summary)
      :description (:description summary)
      :like (get-like-value authority-id item)
      :attachments (-> item :activities :attach)
      :tags (tags-as-string authority-id item)
      :comment ""})))



(defn edit-control [editing?!]
  [button-component {:class "action-button edit-button" :icon-class "icon-edit" :tool-tip-text "Edit"} #(reset! editing?! true)])

(defn get-item [feed entity-id]
  (->> feed :results (some #(when (= entity-id (:entityId %)) %))))

(defn action-summary [persona-id! key action-expanded? activities on-click]
  (let [actions (key activities)
        expanded? (key action-expanded?)
        highlight (some #(= @persona-id! (:authorityId %)) actions)]
    [:span.action-wrapper
     [button-component {:class (str "action-button " (when highlight "action-highlight")) 
                        :icon-class (str "icon-" (name key)) 
                        :text (count actions)
                        :tool-tip-text (string/capitalize (name key))} 
      on-click]
     [button-component {:class "action-toggle-button" 
                        :icon-class (str "icon-" (if @expanded? "hide" "show"))
                        :tool-tip-text (if @expanded? "Hide" "Show")
                        :disabled (not (seq actions))} 
      #(toggle-atom (map second action-expanded?) expanded?)]
     ]))

(defn attachment-summary [persona-id! action-expanded? activities on-change]
  (let [input-id (str (random-uuid))
        distinct-activities {:attach (distinct-attachments persona-id! (:attach activities))}]
    [:span.attachment-wrapper
     [hidden-file-input input-id on-change] 
     [action-summary persona-id! :attach action-expanded? distinct-activities #(.click (js/document.getElementById input-id))]]))

(defn update-display-entity [persona-id feed! edit-item on-error]
  (model/update-entity
   (:context @feed!)
   persona-id
   edit-item
   #(update-feed-item feed! %)
   #(on-error %)))

(defn update-edit-entity [persona-id feed! edit-item! on-success on-error]
  (model/update-entity
   (:context @feed!)
   persona-id
   @edit-item!
   (fn [i] 
     (update-feed-item feed! i)
     (on-success))
   #(on-error %)))

(defn like-item! [persona-id feed! item on-error]
  (let [value (if (get-like-value persona-id item) nil true)
        entity-id (:entityId item)]
    (like-entity!
     (:context @feed!)
     persona-id 
     entity-id 
     value 
     #(update-feed-item feed! %) 
     on-error)))

;; TODO - Combing with tags-edit-control?
(defn tags-control [persona-id! feed! item tagging?!]
  (let [tags!  (atom (:tags item))
        error! (atom nil)]
    (fn []
      (let [on-save (fn []
                      (tag-entity!
                       (:context @feed!)
                       @persona-id!
                       (:entityId item)
                       @tags!
                       #(do
                          (reset! tagging?! false)
                          (update-feed-item feed! %)) 
                       #(reset! error! %)))]
        (when @tagging?!
          [:div.tags-edit-control
           [:div.field-header "Tags:"]
           [:input.tags-text
            {:type "text"
             :value @tags!
             :on-KeyUp #(case (-> % .-keyCode)
                          13 (on-save)
                          27 (reset! tagging?! false)
                          false)
             :on-change #(reset! tags! (-> % .-target .-value))}]
           [edit-control-buttons {:on-save on-save :on-cancel #(reset! tagging?! false)} false error!]])))))


(defn item-activities [persona-id! personas! feed! item editing?! on-click-authority on-click-tag]
  (let [action-expanded? (apply hash-map (mapcat #(vector % (atom false)) [:save :like :tag :comment :attach]))
        tagging? (atom false)
        commenting? (atom false)
        uploading?! (atom false)
        progress! (atom 0)
        context (:context @feed!)
        update-feed-item #(update-feed-item feed! %)
        error! (atom nil)
        on-error #(reset! error! %)
        preview-fn? (fn [] (every? #(not @%) (map second action-expanded?)))]
    (fn []
      (let [entity-id (:entityId item)
            activities (:activities item)]
        [:div.activities-summary
         [:div.activity-buttons 
          (when personas! [persona-select personas! persona-id!])
          [action-summary persona-id! :save action-expanded? activities #(save-item context @persona-id! item update-feed-item on-error)]
          [action-summary persona-id! :like action-expanded? activities #(like-item! @persona-id! feed! item on-error)]
          [action-summary persona-id! :tag action-expanded?  activities #(swap! tagging? not)]
          [attachment-summary
           persona-id!
           action-expanded?
           activities
           #(model/add-attachments context @persona-id! entity-id
                                   % (fn [p] (reset! uploading?! true) (reset! progress! p))
                                   (fn [i] (update-feed-item i) (reset! uploading?! false)) on-error)]
          [action-summary persona-id! :comment action-expanded? activities #(swap! commenting? not)]
          [edit-control editing?!]]
         [:div.activity-block
          [upload-progress uploading?! progress!]
          [tags-control persona-id! feed! item tagging?]
          [comment-control context persona-id! (get-item @feed! entity-id) nil "" commenting? update-feed-item]
          [item-saves (:save action-expanded?) (:save activities) on-click-authority]
          [item-likes (:like action-expanded?) (:like activities) on-click-authority]
          [item-tags (:tag action-expanded?) (:tag activities) on-click-authority on-click-tag]
          [item-comments
           context persona-id!
           (get-item @feed! entity-id)
           (:comment activities) preview-fn?
           (:comment action-expanded?)
           update-feed-item
           on-click-authority]
          [item-attachments
           persona-id!
           (:attach action-expanded?)
           (:attach activities)
           (fn [data-id]
             (model/delete-attachment context @persona-id! entity-id data-id #(update-feed-item %) #(reset! error! %)))
           on-click-authority] 
          [error-control error!]]]))))

(defn posted-by [summary on-click-authority]
  (let [posted-by (:postedBy summary)
        name (:name posted-by)
        origin-distance (or (:originDistance posted-by) 0)
        display-name (if (:isPersona posted-by) (str "You (" name ")") name)] 
    [:div.posted-by
     [profile-img (:imageUri posted-by) name (:id posted-by) #(on-click-authority name)]
     (when (> origin-distance 0) 
       [:span.origin-distance "Unknown via "])
     [:span.authority {:on-click #(on-click-authority name)} display-name]]
    ))

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
        image-uri (:imageUri summary) 
        img [:img.item-img {:src image-uri}]
        posted-by-image-uri (-> summary :postedBy :imageUri)]
    (when (not-empty image-uri)
      [:div.item-img-box
       (if (empty? item-uri)
         img
         [:a {:href item-uri :target "_blank"} img])])))

(defn display-feed-item [persona-id! personas! feed! item editing?! on-click-authority on-click-tag]
  (let [summary (:summary item)
        posted-by-name  (-> summary :postedBy :name)]
    (fn []
      [:div.list-item
       [posted-by summary on-click-authority]
       [item-name summary] 
       [item-tags-summary (-> item :activities :tag) on-click-tag]
       [:div.item-body
        [item-image summary]
        [:div.item-desc [md->component {:class "desc markdown-text"}  (:description summary)]]] 
       [attachments-preview (-> item :activities :attach) true] 
       [item-activities persona-id! personas! feed! item editing?! on-click-authority on-click-tag]
       [item-divider]])))

(defn on-change [item! key]
  #(swap! item! assoc-in [key] %))

(defn name-edit-control [name on-change]
  [:div.item-name
   [:div.field-header "Title:"]
   [text-input name on-change]])

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

(defn tags-edit-control [tags on-change]
  [:div.tags-edit-control
   [:div.field-header "Tags:"]
   [text-input tags on-change]])

(defn description-edit-control [edit-item! state!]
  [:div.description-edit-control
   [:div.item-desc
    [simple-mde (str (:entityId @edit-item!) "-desc") "Type your post here (or paste a url) ..." (:description @edit-item!) state!]]])

(defn attach-files [edit-item! persona-id! fs on-progress on-success on-error] 
  (upload-files
   @persona-id!
   fs
   on-progress
   (fn [r]
     (swap! edit-item! update-in [:attachments] (fn [as] (distinct-by :id (concat as (upload-result-to-attachments r)))))
     (on-success))
   #(on-error %)))

;; TODO: Put error in separate variable - then create and manage edit-iten only in here
(defn edit-item-control [personas! persona-id! item edit-item! error! on-save on-cancel on-delete]
  (let [description-state! (atom nil)
        comment-state! (atom nil)
        expanded?! (atom false)
        tagging?! (atom false)
        commenting?! (atom false)
        uploading?! (atom false)
        progress! (atom 0)
        on-change (partial on-change edit-item!)]
    (fn []
      (let [name-expanded? (or @expanded?! (seq (:name @edit-item!)))
            image-url-expanded? (or @expanded?! (seq (:imageUri @edit-item!)))
            deletable? (some #(= @persona-id! (:authorityId %)) (-> item :activities :save))]
        [:div.list-item
         (when name-expanded?
           [name-edit-control (:name @edit-item!) (on-change :name)])
         [item-tags-summary-from-string (:tags @edit-item!)]
         (when image-url-expanded?
           [image-uri-edit-control edit-item!])
         (when (or name-expanded? image-url-expanded?)
           [:div.field-header "Description:"])
         [description-edit-control edit-item! description-state!]
         [attachments-preview (:attachments @edit-item!) true]
         [:div.activity-buttons
          (when personas!
            [persona-select personas! persona-id!])
          [button-component 
           {:class "action-button" :icon-class "icon-expand" :tool-tip-text "Expand"} 
           #(swap! expanded?! not)] 
          [like-edit-control edit-item!]
          [button-component 
           {:class "action-button" :icon-class "icon-tag" :tool-tip-text "Tag"} 
           #(swap! tagging?! not)] 
          [select-files-control
           [icon {:icon-class "icon-attach"}]
           (fn [fs]
             (reset! uploading?! true)
             (attach-files
              edit-item!
              persona-id!
              fs
              #(reset! progress! %)
              #(reset! uploading?! false)
              #(reset! error! %)))]
          [button-component 
           {:class "action-button" :icon-class "icon-comment" :tool-tip-text "Comment"} 
           #(swap! commenting?! not)]]
         [:div.activity-block
          [upload-progress uploading?! progress!]
          (when @tagging?!
            [tags-edit-control (:tags @edit-item!) (on-change :tags)])
          (when @commenting?!
            [:div.item-comment-edit
             [comment-edit-control (:entity-id @edit-item!) nil comment-state!]])]
         [edit-control-buttons {:on-save (fn []
                                                       ; nil check on comment-state! needed, since it may not be opened
                                           (when @comment-state! (swap! edit-item! assoc-in [:comment] (.value @comment-state!)))
                                           (swap! edit-item! assoc-in [:description] (.value @description-state!))
                                           (on-save @persona-id!))
                                :on-cancel on-cancel
                                :on-delete on-delete}
          deletable? error!]]))))


(defn delete-feed-item [feed! entity-id]
  (swap! feed! update-in [:results] (fn [results] (remove #(= (:entityId %) entity-id) results))))


(defn delete-entity [persona-id feed! editing?! item on-error]
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
     #(on-error %))))


;; TODO: Use keys to get 
(defn edit-feed-item [personas! persona-id! feed! item editing?!]
  (let [edit-item! (atom (edit-item @persona-id! item))
        error! (atom nil)]
    (edit-item-control
     personas!
     persona-id!
     item
     edit-item!
     error!
     (fn []
       (update-edit-entity @persona-id! feed! edit-item! #(reset! editing?! false) #(reset! error! %)))
     #(reset! editing?! false)
     (fn [] ( delete-entity @persona-id! feed! editing?! item #(reset! error! %))))))


(defn feed-item [persona-id personas! feed! item on-click-authority on-click-tag]
  (let [editing?! (atom false)
        persona-id! (atom (or persona-id (:personaId item)))
        personas! (if persona-id nil personas!)]
    (fn [] 
      (if @editing?!
        [edit-feed-item personas! persona-id! feed! item editing?!]
        [display-feed-item persona-id! personas! feed! item editing?! on-click-authority on-click-tag]))))

(defn feed-status [feed!]
  [:div.feed-status
   (let [query (:query @feed!)]
     (when (not-empty query)
       (str (count (:results @feed!)) " results for '" query "'")))])

(defn feed-list [persona-id! personas! feed! on-click-authority on-click-tag] 
  (when @feed!
    [:div.content-list.feed-list
     [feed-status feed!]
     (doall (for [item  (:results @feed!)]
              ^{:key [item @persona-id!]}
              [feed-item @persona-id! personas! feed! item on-click-authority on-click-tag]))]))


(defn prepend-feed-item [feed! view-item]
  (swap! feed! update-in [:results] #(into [view-item] %)))

(defn new-post [persona-id feed! creating-post!? edit-item! on-error]
  (model/new-post
   (:context @feed!)
   persona-id
   @edit-item!
   #(do
      (prepend-feed-item feed! %)
      (reset! creating-post!? false))
   #(on-error %)))

(defn feed-instructions []
  [:div.list-item 
   [:div
    [:img.nola-img {:src "img/nola.png"}]
    [:div.item-name  "Snap! Your feed is empty!"]
    [:div
     [:ul.instruction-items
      [:li [:span "Add posts using the " [:a {:class "item-link" :href "help/help.html#extension"} "browser extension "] " or by clicking the new post icon ("] [:img.header-icon {:src  "../img/new-post.png"}] ") on the top right"]
      [:li "Add peers by clicking the peers icon (" [:img.header-icon {:src  "../img/peers.png"}] ") on the top right"]
      [:li "Browse help by clicking the help icon (" [:img.header-icon {:src  "../img/help.png"}] ") on the top right"]]]]])

(defn toggle-term [string term] 
  (string/trim
   (if (string/includes? string term)
     (string/replace string term "")
     (str string (if (string/blank? string) "" " ") term))))

(defn on-click-tag [on-search query tag] 
  (on-search (toggle-term query (str "#" tag))))

(defn on-click-authority [on-search query authority-name] 
  (let [name (first (string/split (string/replace authority-name #"You \(|\)" "") #"\s+"))]
    (on-search (toggle-term query (str "@" name)))))

;; TODO: Make parameter ordering consistent. Some places have persona-id then personas, others
;; are the other way around.
;; TODO: Only pass atoms when necessary. There are some cases where the personas! is passed, when @personas! 
;; would suffice
(defn feed-page [feed! personas! persona-id! on-persona-select query! on-search]
  (let [creating-post?! (atom false)
        on-click-tag #(on-click-tag on-search @query! %)
        on-click-authority #(on-click-authority on-search @query! %)]
    (fn []
      [:div#opencola.feed-page
       [search/search-header
        :feed
        personas!
        persona-id!
        on-persona-select
        query!
        on-search 
        creating-post?!]
       [error-control (state/error!)]
       (when @creating-post?!
         (let [edit-item! (atom (edit-item))
               error! (atom nil)]
           [:div.content-list.feed-list
            [edit-item-control
             (when (not @persona-id!) personas!)
             (atom (or @persona-id! (-> @personas! first :id)))
             nil
             edit-item!
             error!
             (fn [persona-id] (new-post persona-id feed! creating-post?! edit-item! #(reset! error! %)))
             #(reset! creating-post?! false) nil]
       [error-control error!]]))
       (if (or @creating-post?! (= @feed! {}) (not-empty (:results @feed!)) (not-empty (:query @feed!)))
         [feed-list persona-id! personas! feed! on-click-authority on-click-tag]
         [empty-page-instructions :feed "This persona has no posts!"])])))
