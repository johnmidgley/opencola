(ns ^:figwheel-hooks opencola.search-ui
  (:require
   [goog.dom :as gdom]
   [reagent.core :as reagent :refer [atom]]
   [reagent.dom :as rdom]))

(println "Loaded.")

(defn multiply [a b] (* a b))

;; define your app data so that it doesn't get over-written on reload
(defonce app-state (atom {:query "query"}))

(defn get-app-element []
  (gdom/getElement "app"))

(defn search-box []
  [:div#opencola.search-box 
   [:input {:type "text"
            :value (:query @app-state)
            :on-change #(swap! app-state assoc :query (-> % .-target .-value))}]])

(defn search-header []
  [:div#opencola.search-header "Search Header"
   (search-box)])

(defn search-results [])

(defn search-page []
  [:div#opencola.search-page
   (search-header)
   (search-results)])

(defn mount [el]
  (rdom/render [search-page] el))

(defn mount-app-element []
  (when-let [el (get-app-element)]
    (mount el)))

;; conditionally start your application based on the presence of an "app" element
;; this is particularly helpful for testing this ns without launching the app
(mount-app-element)

;; specify reload hook with ^:after-load metadata
(defn ^:after-load on-reload []
  (mount-app-element)
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
