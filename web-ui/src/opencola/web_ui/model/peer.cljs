(ns opencola.web-ui.model.peer
  (:require
   [opencola.web-ui.ajax :as ajax]
   [opencola.web-ui.model.error :as error]))

(defn get-peers [persona-id on-success on-error]
  (ajax/GET (str "peers?personaId=" persona-id) 
            #(on-success %)
            #(on-error (error/error-result->str %))))

(defn update-peer [persona-id peer on-success on-error]
  (ajax/PUT (str "peers?personaId=" persona-id)
            peer
            #(get-peers persona-id on-success on-error)
            #(on-error (error/error-result->str %))))

(defn delete-peer [persona-id peer on-success on-error]
  (ajax/DELETE 
   (str "peers/" (:id peer))
   #(get-peers persona-id on-success on-error)
   #(on-error (error/error-result->str %))))

(defn get-invite-token [persona-id on-success on-error]
  (ajax/GET (str "peers/token?personaId=" persona-id)
            #(on-success (:token %))
            #(on-error (error/error-result->str %))))

(defn token-to-peer [token on-success on-error]
  (ajax/POST "peers/token"
             {:token token}
             on-success
             #(on-error (error/error-result->str %))))
