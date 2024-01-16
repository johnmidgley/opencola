(ns ^:figwheel-hooks opencola.web-ui.model.action
  (:require
   [opencola.web-ui.ajax :refer [POST]]
   [opencola.web-ui.model.error :refer [error-result->str]]
   [opencola.web-ui.model.feed :refer [model-to-view-item]]))

(defn persist-action! [context persona-id entity-id action payload on-success on-error]
  (POST
    (str "entity/" entity-id "/" action "?context=" context "&personaId=" persona-id)
    payload
    #(on-success (model-to-view-item %))
    #(on-error (error-result->str %))))