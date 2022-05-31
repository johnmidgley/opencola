(ns opencola.web-ui.model.settings 
  (:require
   [opencola.web-ui.ajax :as ajax]
   [opencola.web-ui.model.error :as error]))

;; TODO: Copied from feed.cljs - abstract
(defn set-error-from-result [feed! {status :status text :status-text}]
  (reset! feed! {:error  (str "Error: " status ": " text)}))

(defn get-peers [peers!]
  (ajax/GET "peers" 
            #(reset! peers! %)
            #(set-error-from-result peers! %)) )
