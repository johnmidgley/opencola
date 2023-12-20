(ns ^:figwheel-hooks opencola.web-ui
  (:require
   [goog.dom :as gdom]
   [reagent.dom :as rdom]
   [opencola.web-ui.config :as config]
   [opencola.web-ui.app-state :as state :refer [personas! persona! query! feed! peers! error! themes! theme! set-page!]]
   [opencola.web-ui.view.feed :as feed]
   [opencola.web-ui.view.persona :as persona]
   [opencola.web-ui.view.peer :as peer]
   [opencola.web-ui.view.settings :as settings]
   [opencola.web-ui.location :as location]
   [opencola.web-ui.theme :as theme]
   [secretary.core :as secretary :refer-macros [defroute]]
   [goog.events :as events])
  (:import [goog History]
           [goog.history EventType]))

;; TODO: Move routing to separate file
(secretary/set-config! :prefix "#")

(defroute "/" []
  (state/set-page! :feed))

(defroute "/feed" [query-params]
  (state/set-page! :feed)
  (location/set-state-from-query-params query-params)
  (let [query (or (:q query-params) "")]
    (when @config/config ; Needed when overriding host-url for dev
      (feed/get-feed @(persona!) query (feed!) #(error! %)))))

(defroute "/peers" [query-params]
  (state/set-page! :peers)
  (location/set-state-from-query-params query-params)
  (if (not @(persona!))
    (do
      (persona! (-> @(personas!) first :id))
      (location/set-location-from-state))
    (when @config/config
      (peer/get-peers @(persona!) (peers!) #(error! %)))))

(defroute "/personas" []
  (when @config/config
    (persona/get-personas (personas!) #(error! %)))
  (state/set-page! :personas)
  (persona! nil))

(defroute "/settings" [] 
  (state/set-page! :settings)
  (persona! nil))

(defroute "*" []
  (state/set-page! :error))

(defn get-app-element []
  (gdom/getElement "app"))

(defn on-search [query] 
  (query! query)
  (set-page! :feed)
  (location/set-location-from-state))

(defn on-persona-select [persona-id] 
  (case persona-id
    "" (do (persona! nil) (state/set-page! :feed)) 
    "manage" (location/set-page! :personas)
    (do
      (when (= :personas (state/get-page))
        (state/set-page! :feed))
      (persona! persona-id)))
  (location/set-location-from-state))

(defn app []
  (fn [] 
    [:div.app {:style (get @(themes!) @(theme!))}
     (when (state/page-visible? :feed)
       [feed/feed-page (feed!) (personas!) (persona!) on-persona-select (query!) on-search])
     (when (state/page-visible? :peers)
       [peer/peer-page (peers!) (personas!) (persona!) on-persona-select (query!) on-search])
     (when (state/page-visible? :personas) 
       [persona/personas-page (personas!) (persona!) on-persona-select (query!) on-search])
     (when (state/page-visible? :settings)
       [settings/settings-page (personas!) (persona!) (themes!) (theme!) on-persona-select (query!) on-search])
     (when (state/page-visible? :error)
       [:div.settings "404"])]))


(defn mount [el] 
  (rdom/render [app] el))

(defn mount-app-element [] 
  (when-let [el (get-app-element)] 
    (mount el)))

(defn init [] 
  (persona/init-personas 
   (personas!) 
   (fn []
     (themes! (theme/get-themes))
     (theme! "light") 
     (mount-app-element)
     (location/set-location-from-state)
     (when (empty @(feed!))
       (feed/get-feed @(persona!) @(query!) (feed!) #(error! %)))
     (when-let [persona @(persona!)]
       (peer/get-peers persona (peers!) #(error! %))))
   #(error! %)))

;; conditionally start your application based on the presence of an "app" element
;; this is particularly helpful for testing this ns without launching the app
#_(mount-app-element)

;; specify reload hook with ^:after-load metadata
(defn ^:after-load on-reload [] 
  (mount-app-element)
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)

(defonce history-initiated (doto (History.)
  (events/listen EventType.NAVIGATE #(secretary/dispatch! (.-token %)))
  (.setEnabled true)))

(config/get-config
 #(init)
 #(location/set-location "config-error.html"))



