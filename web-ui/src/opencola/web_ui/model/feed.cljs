(ns ^:figwheel-hooks opencola.web-ui.model.feed 
  (:require
   [opencola.web-ui.ajax :as ajax]
   [opencola.web-ui.model.error :as error]))

(defn error-result->str [{status :status text :status-text}]
  (if (= 0 status)
    "Unable to connect to server. Please make sure it is running."
    text))

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


;; TODO: Remove any mention of feed! in here - should be in view

;; TODO Move to view
(defn update-feed-item [feed! view-item]
  (let [entity-id (:entityId view-item)
        updated-feed (update-in 
                      @feed! 
                      [:results]
                      #(map (fn [i] (if (= entity-id (:entityId i)) view-item i)) %))]
    (reset! feed! updated-feed)))


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
(defn update-entity-old [feed! editing?! item] 
  (ajax/PUT 
   (str "/entity/" (:entityId item))
   item
   #(do (update-feed-item feed! (model-to-view-item %))
        (if editing?! (reset! editing?! false)))
   #(set-error-from-result feed! %)))

(defn update-entity [item on-success on-error] 
  (ajax/PUT 
   (str "/entity/" (:entityId item))
   item
   #(on-success (model-to-view-item %))
   #(on-error (error-result->str %))))


(defn delete-comment [comment-id on-success on-error]  
  (ajax/DELETE
   (str "/comment/" comment-id)
   on-success ;; Follow main pattern and just return whole item? Will mess up UI state, but could ignore result
   #(on-error (error-result->str %))))


;; TODO: Change *-error to *-failure
(defn save-entity [item on-success on-error]
  (ajax/POST 
   (str "/entity/" (:entityId item))
   nil
   #(on-success (model-to-view-item %))
   #(on-error (error-result->str %))))



(defn new-post [item on-success on-error]
  (ajax/POST
   (str "/post")
   item
   #(on-success (model-to-view-item %))
   #(on-error (error-result->str %))))
