(ns ^:figwheel-hooks opencola.web-ui.model.like
  (:require 
   [opencola.web-ui.model.action :refer [persist-action!]]))

(defn like-entity! [context persona-id entity-id value on-success on-error]
  (persist-action! 
   context 
   persona-id 
   entity-id 
   "like" 
   {:value value} 
   on-success 
   on-error))