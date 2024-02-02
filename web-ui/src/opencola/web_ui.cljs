(ns ^:figwheel-hooks opencola.web-ui
  (:require
   [goog.dom :as gdom]
   [reagent.dom :as rdom]
   [opencola.web-ui.config :as config]
   [opencola.web-ui.app-state :as state :refer [personas! persona! query! feed! peers! error! themes! theme! set-page! settings!]]
   [opencola.web-ui.view.feed :as feed]
   [opencola.web-ui.view.persona :as persona]
   [opencola.web-ui.view.peer :as peer]
   [opencola.web-ui.view.settings :as settings]
   [opencola.web-ui.settings :refer [get-settings default-settings]]
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
    nil (do (persona! nil) (state/set-page! :feed))
    "manage" (location/set-page! :personas)
    (do
      (when (= :personas (state/get-page))
        (state/set-page! :feed))
      (persona! persona-id)))
  (location/set-location-from-state))

(defn scroll-handler [f]
  (let [page-y-offset (.-pageYOffset js/window)
        inner-height (.-innerHeight js/window)
        magic-offset-number 200 ; will fail at extreemly large document sizes TODO: make that not so.
        offset-height (.-offsetHeight (.-body js/document))] 
    (when (>= (+ (Math/ceil page-y-offset) inner-height) (- offset-height magic-offset-number)) 
      (f))))

(defn add-scroll-listener [f]
  (js/window.addEventListener "scroll" #(scroll-handler f)))

(defn load-more-items [] 
  (case (state/get-page)
    :feed (feed/paginate-feed @(persona!) @(query!) (feed!) #(error! %))
    #())) ; TODO: update cases here when pagination is needed

(defn app [] 
  (fn []
    [:div.app {:style (theme/get-theme-attributes @(theme!))}
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

(defn init-personas [on-done]
  (persona/init-personas
   (personas!)
   on-done
   #(do (error! %) (on-done))))

(defn init-settings [on-done] 
  (get-settings 
   #(do (settings! %) (on-done)) 
   #(do (error! %) (settings! default-settings) (on-done))))

(defn init-themes [on-done]
  (theme/get-themes
   #(do (themes! %) (on-done))
   #(do (error! %) (themes! theme/default-themes) (on-done))))

(defn init-config [on-success]
  (config/get-config on-success #(location/set-location "config-error.html")))

; TODO: proper chaining of async operations
(init-config 
 (fn [] (init-personas 
         (fn [] (init-settings 
                 (fn [] (init-themes 
                         (fn [] (theme! (:theme-name @(settings!))) 
                           (add-scroll-listener load-more-items)
                           (location/set-location-from-state)
                           (when (empty @(feed!))
                             (feed/get-feed @(persona!) @(query!) (feed!) #(error! %)))
                           (when-let [persona @(persona!)]
                             (peer/get-peers persona (peers!) #(error! %)))
                           (mount-app-element)))))))))