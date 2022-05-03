(ns ^:figwheel-hooks opencola.web-ui
  (:require
   [goog.dom :as gdom]
   [reagent.core :as reagent :refer [atom]]
   [reagent.dom :as rdom]
   [cljs-time.coerce :as c]
   [cljs-time.format :as f]
   [lambdaisland.uri :refer [uri]]
   [opencola.web-ui.config :as config]
   [opencola.web-ui.ajax :as ajax]
   [opencola.web-ui.feed :as feed]))


(defonce error-message (atom nil))

(defn error-handler [{:keys [status status-text]}]
  (reset! error-message (str "Error: " status ": " status-text)))

(defn get-app-element []
  (gdom/getElement "app"))


(defn mount [el]
  (rdom/render 
   [:div.app
    [:div.error @error-message]
    [feed/feed-page]] el))

(defn mount-app-element []
  (when-let [el (get-app-element)]
    (mount el)))

;; conditionally start your application based on the presence of an "app" element
;; this is particularly helpful for testing this ns without launching the app
#_(mount-app-element)
(config/get-config #(mount-app-element) error-handler)

;; specify reload hook with ^:after-load metadata
(defn ^:after-load on-reload []
  (mount-app-element)
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)



