(ns ^:figwheel-hooks opencola.web-ui.model.feed 
  (:require
   [opencola.web-ui.ajax :as ajax]
   [opencola.web-ui.model.error :as error]))

(defn set-error-from-result [feed! {status :status text :status-text}]
  (reset! feed! {:error  (str "Error: " status ": " text)}))

(defn set-query [feed query]
  (if query (assoc-in feed [:query] query)))

(defn model-to-view-item [model-item]
  (update model-item 
          :activities
          (fn [activities]
            (->> activities
                 (mapcat
                  (fn [activity]
                    (let [activity-no-actions (dissoc activity :actions)]
                      (map #(merge activity-no-actions %) (:actions activity)))))
                 (group-by #(keyword (:type %)))))))

(defn feed-to-view-model [feed query]
  (-> feed 
      (update :results #(map model-to-view-item %))
      (set-query query)))

(defn update-feed-item [feed! view-item]
  (let [entity-id (:entityId view-item)
        updated-feed (update-in 
                      @feed! 
                      [:results]
                      #(map (fn [i] (if (= entity-id (:entityId i)) view-item i)) %))]
    (reset! feed! updated-feed)))

(defn prepend-feed-item [feed! view-item]
  (swap! feed! update-in [:results] #(into [view-item] %)))

(defn delete-feed-item [feed! entity-id]
  (swap! feed! update-in [:results] (fn [results] (remove #(= (:entityId %) entity-id) results))))


(defn get-feed [query feed!]
  (ajax/GET (str "feed" "?q=" query) 
            #(reset! feed! (feed-to-view-model % query))
            #(set-error-from-result feed! %)))

(defn delete-entity [feed! editing?! entity-id]
  (ajax/DELETE (str "entity/" entity-id) 
               (fn [model-item]
                 (if (empty? model-item)
                   (delete-feed-item feed! entity-id)
                   (update-feed-item feed! (model-to-view-item model-item)))
                 (if editing?! (reset! editing?! false)))
               #(set-error-from-result feed! %))) 


;; TODO: Break out view and model here - feed should be controlled by model, but editing should be controlled
;; by view. 
(defn comment-handler [feed! editing?! model-item]
  (update-feed-item feed! (model-to-view-item model-item))
  (reset! editing?! false))


;; TODO: Change to update comment
(defn add-comment [feed! entity-id comment-id text editing?!]
  (ajax/POST (str "entity/" entity-id "/comment") 
             {:commentId comment-id
              :text text}
             #(comment-handler feed! editing?! %)
             #(set-error-from-result feed! %)))

(defn update-item-error-handler [feed! response]
  (set-error-from-result feed! response))

;; TODO: editing?! should not be passed in here. Make it part of the actual view model, that gets
;; overwritten when reloaded from client. 
(defn update-entity [feed! editing?! item] 
  (ajax/PUT 
   (str "/entity/" (:entityId item))
   item
   #(do (update-feed-item feed! (model-to-view-item %))
        (if editing?! (reset! editing?! false)))
   #(set-error-from-result feed! %)))

(defn get-item [feed entity-id]
  (->> feed :results (some #(if (= entity-id (:entityId %)) %))))

(defn remove-comment [item comment-id]
  (update-in item 
             [:activities :comment]
             (fn [comments]
               (filterv #(not= comment-id (:id %)) comments))))

(defn delete-comment [feed! entity-id comment-id]  
  (let [item (get-item @feed! entity-id)]
    (ajax/DELETE
     (str "/comment/" comment-id)
     #(update-feed-item feed! (remove-comment item comment-id))
     #(set-error-from-result feed! %))))

(defn save-entity [feed! view-item]
  (ajax/POST 
   (str "/entity/" (:entityId view-item))
   nil
   #(update-feed-item feed! (model-to-view-item %))
   #(set-error-from-result feed! %)))

(defn like-entity [feed! view-item]
  (ajax/PUT 
   (str "/entity/" (:entityId view-item))
   nil
   #(update-feed-item feed! (model-to-view-item %))
   #(set-error-from-result feed! %)))

(defn new-post [feed! editing?! item]
  (ajax/POST
   (str "/post")
   item
   #(do (prepend-feed-item feed! (model-to-view-item %))
        (if editing?! (reset! editing?! false)))
   #(set-error-from-result feed! %)))
