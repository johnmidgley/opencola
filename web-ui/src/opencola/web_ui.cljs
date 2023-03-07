(ns ^:figwheel-hooks opencola.web-ui
  (:require
   [goog.dom :as gdom]
   [reagent.core :as reagent :refer [atom]]
   [reagent.dom :as rdom]
   [opencola.web-ui.config :as config]
   [opencola.web-ui.app-state :as state :refer [personas! persona! query! feed! peers! error!]]
   [opencola.web-ui.common :as common]
   [opencola.web-ui.view.feed :as feed]
   [opencola.web-ui.view.persona :as persona]
   [opencola.web-ui.view.peer :as peer]
   [opencola.web-ui.model.error :as error]
   [opencola.web-ui.location :as location]
   [secretary.core :as secretary :refer-macros [defroute]]
   [goog.events :as events])
  (:import [goog History]
           [goog.history EventType]))


;; TODO: Move routing to separate file
(secretary/set-config! :prefix "#")

(defroute "/" []
  (state/set-page! :feed)
  (location/set-state-from-query-params nil))

(defroute "/feed" [query-params]
  (state/set-page! :feed)
  (location/set-state-from-query-params query-params)
  (let [query (or (:q query-params) "")]
    (if @config/config ; Needed when overriding host-url for dev
      (feed/get-feed @(persona!) query (feed!)))))

(defroute "/peers" [query-params]
  (state/set-page! :peers)
  (location/set-state-from-query-params query-params)
  (if (not @(persona!))
    (do
      (persona! (-> @(personas!) :items first :id))
      (location/set-location-from-state))
    (if @config/config
      (peer/get-peers @(persona!) (peers!)))))

(defroute "/personas" [query-params]
  (if @config/config
    (persona/get-personas (personas!)))
  (state/set-page! :personas)
  (persona! nil))

(defroute "*" []
  (state/set-page! :error))

(defn get-app-element []
  (gdom/getElement "app"))

(defn on-search [query] 
  (query! query)
  (location/set-location-from-state))

(defn on-persona-select [persona]
  (case persona
    "" (location/set-location "/#/feed")
    "manage" (location/set-location "/#/personas")
    (do
     (if (= :personas (state/get-page))
       (state/set-page! :feed))
     (persona! persona)
     (location/set-location-from-state))))

(defn app []
  (fn []
    [:div.app
     (if (state/page-visible? :feed)
       [feed/feed-page (feed!) (personas!) (persona!) on-persona-select (query!)  on-search])
     (if (state/page-visible? :peers)
       [peer/peer-page (peers!) (personas!) (persona!) on-persona-select (query!) on-search])
     (if (state/page-visible? :personas)
       [persona/personas-page (personas!) (persona!) on-persona-select (query!) on-search])
     (if (state/page-visible? :error)
       [:div.settings "404"])
     [error/error-control @(error!)]]))


(defn mount [el]
  (rdom/render [app] el))

(defn mount-app-element []
  (when-let [el (get-app-element)]
    (mount el)))

(defn init-personas []
  (persona/init-personas 
   (personas!)
   (fn []
     (location/set-location-from-state)
     (feed/get-feed @(persona!) @(query!) (feed!))
     (when-let [persona @(persona!)]
       (peer/get-peers persona (peers!))))
   #(error/set-error! (error!) %)))

;; conditionally start your application based on the presence of an "app" element
;; this is particularly helpful for testing this ns without launching the app
#_(mount-app-element)
(config/get-config 
 #(do (mount-app-element)
      (init-personas)) 
 #(error/set-error! (error!) %))

;; specify reload hook with ^:after-load metadata
(defn ^:after-load on-reload []
  (mount-app-element)
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)

(doto (History.)
  (events/listen EventType.NAVIGATE #(secretary/dispatch! (.-token %)))
  (.setEnabled true))



