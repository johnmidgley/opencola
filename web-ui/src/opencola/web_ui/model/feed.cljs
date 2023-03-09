(ns ^:figwheel-hooks opencola.web-ui.model.feed 
  (:require
   [opencola.web-ui.ajax :as ajax]
   [opencola.web-ui.model.error :as error]))

(defn error-result->str [{status :status text :status-text response :response}]
  (if (= 0 status)
    "Unable to connect to server. Please make sure it is running."
    (:message response)))

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


(defn get-feed [context persona-id query on-success on-error]
  (ajax/GET (str "feed?context=" context  "&personaId=" persona-id  "&q=" query "") 
            #(on-success (feed-to-view-model % query))
            #(on-error (error-result->str %)))) 

(defn delete-entity [context persona-id entity-id on-success on-error]
  (ajax/DELETE 
   (str "entity/" entity-id "?context=" context  "&personaId=" persona-id)
   #(on-success (model-to-view-item %))
   #(on-error (error-result->str %))))


(defn update-comment [context persona-id entity-id comment-id text on-success on-error]
  (ajax/POST (str "entity/" entity-id "/comment?context=" context "&personaId=" persona-id) 
             {:commentId comment-id
              :text text}
             #(on-success (model-to-view-item %))
             #(on-error (error-result->str %))))

(defn update-entity [context persona-id item on-success on-error]
  ;; TODO - Weird to dissoc a view value here. Think about proper binder
  (let [item (dissoc item :error-message)]
   (ajax/PUT 
    (str "entity/" (:entityId item) "?context=" context "&personaId=" persona-id)
    item
    #(on-success (model-to-view-item %))
    #(on-error (error-result->str %)))))


(defn delete-comment [context persona-id comment-id on-success on-error]  
  (ajax/DELETE
   (str "/comment/" comment-id "?context=" context "&personaId=" persona-id)
   on-success ;; Follow main pattern and just return whole item? Will mess up UI state, but could ignore result
   #(on-error (error-result->str %))))

;; TODO: Change *-error to *-failure
(defn save-entity [context persona-id item on-success on-error]
  (ajax/POST 
   (str "/entity/" (:entityId item) "?context=" context "&personaId=" persona-id)
   nil
   #(on-success (model-to-view-item %))
   #(on-error (error-result->str %))))

(defn new-post [context persona-id item on-success on-error]
  (ajax/POST
   (str "post?context=" context "&personaId=" persona-id)
   item
   #(on-success (model-to-view-item %))
   #(on-error (error-result->str %))))
