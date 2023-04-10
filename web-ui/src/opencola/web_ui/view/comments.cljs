(ns opencola.web-ui.view.comments
  (:require
   [reagent.core :as reagent :refer [atom]]
   [opencola.web-ui.model.error :as error]
   [opencola.web-ui.model.feed :as model]
   [opencola.web-ui.time :refer [format-time]]
   [opencola.web-ui.view.common :refer [action-img md->component]]))

(defn remove-comment [item comment-id]
  (update-in item
             [:activities :comment]
             (fn [comments]
               (filterv #(not= comment-id (:id %)) comments))))

(defn comment-control [context persona-id! item comment-id text expanded?! on-update]
  (let [entity-id (:entityId item)
        text! (atom text)
        error! (atom {})
        on-error #(error/set-error! error! %)]
    (fn []
      (when @expanded?!
        [:div.item-comment
         [:div.item-comment-edit
          [:textarea.comment-text-edit {:type "text"
                                        :value @text!
                                        :on-change #(reset! text! (-> % .-target .-value))}]
          [error/error-control @error!]
          [:button {:on-click #(model/update-comment context @persona-id! entity-id comment-id @text! on-update on-error)}
           "Save"] " "
          [:button {:on-click #(reset! expanded?! false)} "Cancel"]
          (when comment-id
            [:button.delete-button
             {:on-click #(model/delete-comment
                          context
                          @persona-id!
                          comment-id
                          (fn [] (on-update (remove-comment item comment-id)))
                          on-error)} "Delete"])]]))))

(defn item-comment [context persona-id! item comment-action on-update]
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
           [comment-control context persona-id! item comment-id text editing?! on-update]
           [:div.item-comment-container [md->component {:class "item-comment-text"} text]])]))))

(defn item-comments [context persona-id! item comment-actions preview-fn? expanded?! on-update]
  (let [preview? (preview-fn?)
        more (- (count comment-actions) 3)
        comment-actions (if preview? (take 3 comment-actions) comment-actions)]
    (when (or @expanded?! (and preview? (not-empty comment-actions)))
      [:div.item-comments
       [:span {:on-click (fn [] (swap! expanded?! #(not %)))} "Comments:"]
       (doall (for [comment-action comment-actions]
                ^{:key comment-action} [item-comment context persona-id! item comment-action on-update]))
       [:div.item-comments-footer {:on-click (fn [] (swap! expanded?! #(not %)))}
        (when (> more 0)
          (if preview?
            [:span.action "Show more" (action-img "show")]
            [:span.action "Show less" (action-img "hide")]))]])))