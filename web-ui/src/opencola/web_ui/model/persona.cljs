(ns opencola.web-ui.model.persona
  (:require
   [opencola.web-ui.ajax :as ajax]
   [opencola.web-ui.model.error :as error]))

(defn get-personas [on-success on-error]
  (ajax/GET "personas" 
            #(on-success %)
            #(on-error (error/error-result->str %))))

(defn update-persona [persona on-success on-error]
  (ajax/PUT "persona"
            persona
            #(get-personas on-success on-error)
            #(on-error (error/error-result->str %))))

(defn delete-persona [persona on-success on-error]
  (ajax/DELETE 
   (str "persona/" (:id persona))
   #(get-personas on-success on-error)
   #(on-error (error/error-result->str %))))
