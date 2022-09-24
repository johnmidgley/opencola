(ns opencola.web-ui.model.peer
  (:require
   [opencola.web-ui.ajax :as ajax]
   [opencola.web-ui.model.error :as error]))

(defn get-peers [on-success on-error]
  (ajax/GET "peers" 
            #(on-success %)
            #(on-error (error/error-result->str %))))

(defn update-peer [peer on-success on-error]
  (ajax/PUT "peers"
            peer
            #(get-peers on-success on-error)
            #(on-error (error/error-result->str %))))

(defn delete-peer [peer on-success on-error]
  (ajax/DELETE 
   (str "peers/" (:id peer))
   #(get-peers on-success on-error)
   #(on-error (error/error-result->str %))))

(defn get-invite-token [on-success on-error]
  (ajax/GET "peers/token"
            #(on-success (:token %))
            #(on-error (error/error-result->str %))))

(defn token-to-peer [token on-success on-error]
  (ajax/POST "peers/token"
             {:token token}
             on-success
             #(on-error (error/error-result->str %))))
