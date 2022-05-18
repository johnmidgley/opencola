(ns ^:figwheel-hooks opencola.web-ui.model.feed 
  (:require
   [opencola.web-ui.ajax :as ajax]
   [opencola.web-ui.model.error :as error]))

(defn set-error-from-result [feed! {status :status text :status-text}]
  (reset! feed! {:error  (str "Error: " status ": " text)}))

(defn get-feed [q feed!]
  (ajax/GET (str "feed" "?q=" q) 
            #(reset! feed! (assoc-in % [:query] q))
            #(set-error-from-result feed! %)))

(defn delete-entity-handler [feed! entity-id response]
  (swap! feed! update-in [:results] (fn [results] (remove #(= (:entityId %) entity-id) results))))

(defn delete-entity [feed! entity-id]
  (ajax/DELETE (str "entity/" entity-id) 
               (partial delete-entity-handler feed! entity-id)
               #(set-error-from-result feed! %))) 


;; TODO: Break out view and model here - feed should be controlled by model, but editing should be controlled
;; by view. 
(defn comment-handler [feed! editing?! item]
  (let [entity-id (:entityId item)
        updated-feed (update-in 
                      @feed! 
                      [:results]
                      #(map (fn [i] (if (= entity-id (:entityId i)) item i)) %))]
    (reset! feed! updated-feed))
  (reset! editing?! false))

(defn add-comment [feed! entity-id editing?! text]
  (ajax/POST (str "entity/" entity-id "/comment") 
             {:text text }
             #(comment-handler feed! editing?! %)
             #(set-error-from-result feed! %)))

(defn update-feed-item [feed! item]
  (let [entity-id (:entityId item)
        updated-feed (update-in 
                      @feed! 
                      [:results]
                      #(map (fn [i] (if (= entity-id (:entityId i)) item i)) %))]
    (reset! feed! updated-feed)))

(defn update-item-error-handler [feed! response]
  (set-error-from-result feed! response))

;; TODO: editing?! should not be passed in here. Make it part of the actual view model, that gets
;; overwritten when reloaded from client. 
(defn update-entity [feed! editing?! item] 
  (ajax/POST 
   (str "/entity/" (:entityId item))
   item
   #(do (update-feed-item feed! %)
        (reset! editing?! false))
   #(set-error-from-result feed! %)))

(defn delete-comment [feed! entity-id comment-id]  
  (let [item (->> @feed! :results (some #(if (= entity-id (:entityId %)) %)))]
    (println item)))
