(ns ^:figwheel-hooks opencola.web-ui.model.feed 
  (:require
   [opencola.web-ui.ajax :as ajax]
   [opencola.web-ui.model.error :as error]))


(defn get-feed 
  ([q view-model] (get-feed q view-model nil nil))
  ([q view-model on-success] (get-feed q view-model on-success nil))
  ([q view-model on-success on-error]
   (ajax/GET (str "feed" "?q=" q) 
             #(do (reset! view-model %)
                  (when on-success (on-success %)))
             #(do (error/error-handler %)       
                  (when on-error (on-error %))))))

(defn delete-entity-handler [feed entity-id response]
  (swap! feed update-in [:results] (fn [results] (remove #(= (:entityId %) entity-id) results))))

(defn delete-entity [feed entity-id]
  (ajax/DELETE (str "entity/" entity-id) 
               (partial delete-entity-handler feed entity-id)
               error/error-handler)) 


;; TODO: Break out view and model here - feed should be controlled by model, but editing should be controlled
;; by view. 
(defn comment-handler [feed editing? item]
  (let [entity-id (:entityId item)
        updated-feed (update-in 
                      @feed 
                      [:results]
                      #(map (fn [i] (if (= entity-id (:entityId i)) item i)) %))]
    (reset! feed updated-feed))
  (reset! editing? false))

(defn add-comment [feed entity-id editing? text]
  (ajax/POST (str "entity/" entity-id "/comment") 
             {:text text }
             (partial comment-handler feed editing?)
             error/error-handler))

(defn update-item-handler [feed editing? item response]
  (let [entity-id (:entityId response)
        updated-feed (update-in 
                      @feed 
                      [:results]
                      #(map (fn [i] (if (= entity-id (:entityId i)) response i)) %))]
    (reset! feed updated-feed))
  (reset! editing? false))


(defn update-item-error-handler [editing? response]
  (error/error-handler response)
  (reset! editing? false))

(defn update-entity [feed editing? item] 
  (ajax/POST 
   (str "/entity/" (:entityId item))
   item
   (partial update-item-handler feed editing? item)
   (partial update-item-error-handler editing?)))



