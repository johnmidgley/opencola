(ns opencola.web-ui.model.persona
  (:require
   [opencola.web-ui.ajax :as ajax]
   [opencola.web-ui.model.error :refer [error-result->str]]))

(defn get-personas [on-success on-error]
  (ajax/GET "personas" 
            #(on-success %)
            #(on-error (error-result->str %))))

(defn create-persona [persona on-success on-error]
  (ajax/POST "personas"
            persona
            #(get-personas on-success on-error)
            #(on-error (error-result->str %))))

(defn update-persona [persona on-success on-error]
  (ajax/PUT "personas"
            persona
            #(get-personas on-success on-error)
            #(on-error (error-result->str %))))

(defn delete-persona [persona on-success on-error]
  (ajax/DELETE 
   (str "personas/" (:id persona))
   #(get-personas on-success on-error)
   #(on-error (error-result->str %))))
