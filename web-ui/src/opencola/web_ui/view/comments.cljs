(ns opencola.web-ui.view.comments
  (:require
   [reagent.core :as reagent :refer [atom]] 
   [opencola.web-ui.model.feed :as model]
   [opencola.web-ui.time :refer [format-time]]
   [opencola.web-ui.view.common :refer [md->component simple-mde collapsable-list
                                        button-component edit-control-buttons]]))

(defn comment-edit-control [id text state! text-prompt] 
  [:div.comment-edit-control
   [simple-mde (str id "-cmt") (or text-prompt "Enter your comment....") text state!]])

(defn remove-comment [item comment-id]
  (update-in item
             [:activities :comment]
             (fn [comments]
               (filterv #(not= comment-id (:id %)) comments))))

(defn comment-control [context persona-id! item comment-id text expanded?! on-update]
  (let [entity-id (:entityId item)
        state! (atom text)
        error! (atom nil)
        on-error #(reset! error! %)]
    (fn []
      (when @expanded?!
        [:div.item-comment
         [:div.item-comment-edit
          [comment-edit-control (:entity-id item) text state!]
          [edit-control-buttons {
                                 :on-save #(model/update-comment context @persona-id! entity-id comment-id (.value @state!) on-update on-error)
                                 :on-cancel #(reset! expanded?! false)
                                 :on-delete #(model/delete-comment
                                              context
                                              @persona-id!
                                              comment-id
                                              (fn [] (on-update (remove-comment item comment-id)))
                                              on-error)
          } expanded?! error!]]]))))


(defn create-comment-control [id original-text on-save on-cancel on-delete error! config]
  (let [state! (atom nil)
        {text-prompt :text-prompt} config]
    [:div.item-comment
     [:div.item-comment-edit
      [comment-edit-control id original-text state! text-prompt]
      [edit-control-buttons {:on-save #(on-save (.value @state!))
                             :on-cancel on-cancel
                             :on-delete on-delete} on-delete error!]]]))

(defn item-comment [context persona-id! item comment-action on-update on-click-authority]
  (let [editing?! (atom false)
        replying?! (atom false)
        error! (atom nil)
        on-error #(reset! error! %)]
    (fn []
      (let [{authority-id :authorityId
             authority-name :authorityName
             epoch-second :epochSecond
             text :value
             comment-id :id
             replies :replies} comment-action
            editable? (= authority-id @persona-id!)]
        (when (not editable?)
          (reset! editing?! false))
        [:div.item-comment
         (if (and editable? @editing?!)
           [comment-control context persona-id! item comment-id text editing?! on-update]
           [:div.item-comment-container [md->component {:class (str "item-comment-text markdown-text " (when editable? "own-comment"))} text]]) 
         [:div.item-attribution
          [:span.authority {:on-click #(on-click-authority authority-name)} authority-name] " " (format-time epoch-second) " "
          [button-component {:icon-class "icon-reply" :class "comment-button" :tool-tip-text "Reply"} #(swap! replying?! not)]
          (when editable?
            [button-component {:class "comment-button edit-comment-button" :icon-class "icon-edit"} #(swap! editing?! not)])]
         (when @replying?!
           [:reply-block
            [create-comment-control
             (:entityId item)
             ""
             #(model/update-comment context @persona-id! comment-id nil % on-update on-error)
             #(reset! replying?! false)
             nil
             error!
             {:text-prompt "Enter your reply..."}]])
         (when replies
           [:div.replies
            (doall (for [comment-action replies]
                     ^{:key comment-action} [item-comment context persona-id! item comment-action on-update on-click-authority]))])]))))

(defn get-top-level-comment-id [comment-actions comment-action]
  (if-let [parent-id (:parentId comment-action)]
    (get-top-level-comment-id 
     comment-actions 
     (first (filter #(= parent-id (:id %)) comment-actions)))
    (:id comment-action)))

(defn get-comment-groups [comment-actions]
  (let [groups (group-by #(nil? (:parentId %)) comment-actions)
        top-level (get groups true)
        replies (get groups false)
        reply-groups (group-by 
                      #(get-top-level-comment-id comment-actions %) 
                      replies)
        comment-groups (map #(assoc % :replies (get reply-groups (:id %))) top-level)]
    (sort-by #(- (:epochSecond %)) comment-groups)))


(defn item-comments [context persona-id! item comment-actions preview-fn? expanded?! on-update on-click-authority]
  (let [preview? (preview-fn?)
        comment-groups (get-comment-groups comment-actions)
        comment-actions (if preview? (take 3 comment-actions) comment-actions) 
        more (- (count comment-groups) 3)]
    (when (or @expanded?! (and preview? (not-empty comment-actions)))
      [:div.item-comments
       [:span {:on-click (fn [] (swap! expanded?! #(not %)))} "Comments:"]
       (doall (for [comment-action comment-groups]
                ^{:key comment-action} [item-comment context persona-id! item comment-action on-update on-click-authority]))
       (when (> more 0)
         [button-component {
                            :class "expand-button "
                            :text (if preview? "Show more" "Show less")
                            :icon-class (if preview? "icon-show" "icon-hide")
                            } 
          (fn [] (swap! expanded?! #(not %)))])])))