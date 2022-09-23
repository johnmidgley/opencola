(ns ^:figwheel-hooks opencola.web-ui.model.feed 
  (:require
   [opencola.web-ui.ajax :as ajax]
   [opencola.web-ui.model.error :as error]))

(defn error-result->str [{status :status text :status-text response :response}]
  (if (= 0 status)
    "Unable to connect to server. Please make sure it is running."
    (:message response)))

(defn set-error-from-result [feed! {status :status text :status-text}]
  (reset! feed! {:error  (str "Error: " status ": " text)}))

(defn set-query [feed query]
  (if query (assoc-in feed [:query] query)))

(defn model-to-view-item [model-item]
  (if (not-empty model-item)
    (update model-item 
            :activities
            (fn [activities]
              (->> activities
                   (mapcat
                    (fn [activity]
                      (let [activity-no-actions (dissoc activity :actions)]
                        (map #(merge activity-no-actions %) (:actions activity)))))
                   (group-by #(keyword (:type %))))))))

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


(defn get-feed [query feed!]
  (ajax/GET (str "feed" "?q=" query) 
            #(reset! feed! (feed-to-view-model % query))
            #(set-error-from-result feed! %))) 

(defn delete-entity [entity-id on-success on-error]
  (ajax/DELETE 
   (str "entity/" entity-id)
   #(on-success (model-to-view-item %))
   #(on-error (error-result->str %))))


(defn update-comment [entity-id comment-id text on-success on-error]
  (ajax/POST (str "entity/" entity-id "/comment") 
             {:commentId comment-id
              :text text}
             #(on-success (model-to-view-item %))
             #(on-error (error-result->str %))))

(defn update-entity [item on-success on-error]
  ;; TODO - Weird to dissoc a view value here. Think about proper binder
  (let [item (dissoc item :error-message)]
   (ajax/PUT 
    (str "/entity/" (:entityId item))
    item
    #(on-success (model-to-view-item %))
    #(on-error (error-result->str %)))))


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
