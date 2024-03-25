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

(ns opencola.web-ui.view.comments
  (:require
   [reagent.core :as r] 
   [opencola.web-ui.model.feed :as model]
   [opencola.web-ui.time :refer [format-time pretty-format-time]]
   [opencola.web-ui.view.common :refer [md->component simple-mde button-component edit-control-buttons tool-tip]]))

(defn comment-edit-control [id text state! text-prompt control-buttons-config error!] 
  [:div.comment-edit-control
   [simple-mde (str id "-cmt") (or text-prompt "Enter your comment....") text state!]
   (when control-buttons-config [edit-control-buttons control-buttons-config error!])])

(defn remove-comment [item comment-id]
  (update-in item
             [:activities :comment]
             (fn [comments]
               (filterv #(not= comment-id (:id %)) comments))))


(defn comment-control [context persona-id! item comment-id text expanded?! on-update]
  (let [entity-id (:entityId item)
        state! (r/atom text)
        error! (r/atom nil)
        on-error #(reset! error! %)]
    (fn []
      (when @expanded?!
        [:div.item-comment
         [:div.item-comment-edit
          [comment-edit-control 
           (:entity-id item) 
           text 
           state!
           nil
           {:on-save #(model/update-comment context @persona-id! entity-id comment-id (.value @state!) on-update on-error)
            :on-cancel #(reset! expanded?! false)
            :on-delete (when comment-id #(model/delete-comment
                               context
                               @persona-id!
                               comment-id
                               (fn [result-item] (on-update result-item))
                               on-error))} 
           error!]]]))))


(defn create-comment-control [id original-text on-save on-cancel on-delete error! config]
  (let [state! (r/atom nil)
        {text-prompt :text-prompt} config]
    (fn []
      [:div.item-comment
       [:div.item-comment-edit
        [comment-edit-control 
         id 
         original-text 
         state! 
         text-prompt 
         {:on-save #(on-save (.value @state!))
          :on-cancel on-cancel
          :on-delete on-delete} 
         error!]]])))

(defn base-comment [context persona-id! item comment-action on-update on-click-authority]
  (let [editing?! (r/atom false)
        replying?! (r/atom false)
        error! (r/atom nil)
        on-error #(reset! error! %)]
    (fn []
      (let [{authority-id :authorityId
             authority-name :authorityName
             epoch-second :epochSecond
             parent-id :parentId
             text :value
             comment-id :id} comment-action
            editable? (= authority-id @persona-id!)
            parent-comment (when parent-id (first (filter #(= (:id %) parent-id) (-> item :activities :comment))))
            reply-header (when parent-comment (str "@" (:authorityName parent-comment) ":"))] 
        (when (not editable?)
          (reset! editing?! false))
        [:div.base-comment
         (when reply-header [:span.reply-header {:on-click #(on-click-authority authority-name)} reply-header])
         (if (and editable? @editing?!)
           [comment-control context persona-id! item comment-id text editing?! on-update]
           [:div.item-comment-container 
            [md->component {:class (str "item-comment-text markdown-text " (when editable? "own-comment"))} text]])
         [:div.item-attribution
          [:span.authority-time 
           [:span.authority {:on-click #(on-click-authority authority-name)} authority-name] 
           " (" 
           (pretty-format-time epoch-second) 
           ") " 
           [tool-tip {:text (format-time epoch-second) :tip-position "tip-bottom"}]]
          [button-component {:icon-class "icon-reply" :class "comment-button" :tool-tip-text "Reply"} #(swap! replying?! not)]
          (when editable?
            [button-component {:class "comment-button edit-comment-button" :icon-class "icon-edit" :tool-tip-text "Edit"} #(swap! editing?! not)])]
         (when @replying?!
           [:reply-block
            [create-comment-control
             (:entityId item)
             ""
             #(model/update-comment context @persona-id! comment-id nil % on-update on-error)
             #(reset! replying?! false)
             nil
             error!
             {:text-prompt "Enter your reply..."}]])]))))


(defn item-comment [context persona-id! item comment-action on-update on-click-authority]
  (let [expanded?! (r/atom false)]
    (fn [] 
      [:div.item-comment
       [base-comment context persona-id! item comment-action on-update on-click-authority]
       (when-let [replies (sort-by :epochSecond (:replies comment-action))] 
         (let [more (- (count replies) 3)
               display-replies (if @expanded?! replies (take-last 3 replies))] 
           [:div.replies 
            (doall (for [comment-action display-replies]
                     ^{:key comment-action} [base-comment context persona-id! item comment-action on-update on-click-authority]))
            (when (> more 0)
              [button-component {:class "expand-button expand-replies"
                                 :text (if @expanded?! "Show less" (str "Show " more " more repl" (if (> more 1) "ies" "y")))
                                 :icon-class (if @expanded?! "icon-hide" "icon-show")}
               (fn []
                 (swap! expanded?! #(not %)))])]))])))

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
        comment-actions (if preview? (take 3 comment-groups) comment-groups) 
        more (- (count comment-groups) 3)]
    (when (or @expanded?! (and preview? (not-empty comment-actions)))
      [:div.item-comments
       [:span {:on-click (fn [] (swap! expanded?! #(not %)))} "Comments:"]
       (doall (for [comment-action comment-actions]
                ^{:key comment-action} [item-comment context persona-id! item comment-action on-update on-click-authority]))
       (when (> more 0)
         [button-component {
                            :class "expand-button "
                            :text (if preview? (str "Show " more " more comment" (when (> more 1) "s")) "Show less")
                            :icon-class (if preview? "icon-show" "icon-hide")
                            } 
          (fn [] (swap! expanded?! #(not %)))])])))