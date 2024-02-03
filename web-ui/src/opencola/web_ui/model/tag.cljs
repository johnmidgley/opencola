(ns ^:figwheel-hooks opencola.web-ui.model.tag
  (:require 
   [opencola.web-ui.model.action :refer [persist-action!]]))

(defn tag-entity! [context persona-id entity-id value on-success on-error]
  (persist-action! 
   context
   persona-id 
   entity-id 
   "tags" 
   {:value value} 
   on-success 
   on-error))