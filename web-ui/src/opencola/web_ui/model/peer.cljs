(ns opencola.web-ui.model.peer
  (:require
   [opencola.web-ui.ajax :as ajax]
   [opencola.web-ui.model.error :as error]))

(defn get-peers [peers!]
  (ajax/GET "peers" 
            #(reset! peers! %)
            error/error-handler)) ; TODO: Make error handler optional - otherwise default to this

(defn update-peer [peers! peer]
  (ajax/PUT "peers"
            peer
            #(get-peers peers!)
            error/error-handler))


(defn delete-peer [peers! peer]
  (ajax/DELETE (str "peers/" (:id peer))
               #(get-peers peers!)
               error/error-handler))
