(ns ^:figwheel-hooks opencola.web-ui
  (:require
   [goog.dom :as gdom]
   [reagent.core :as reagent :refer [atom]]
   [reagent.dom :as rdom]
   [opencola.web-ui.config :as config]
   [opencola.web-ui.app-state :as state :refer [personas! query! feed! peers! error!]]
   [opencola.web-ui.common :as common]
   [opencola.web-ui.view.feed :as feed]
   [opencola.web-ui.view.persona :as persona]
   [opencola.web-ui.view.peer :as peer]
   [opencola.web-ui.model.error :as error]
   [secretary.core :as secretary :refer-macros [defroute]]
   [goog.events :as events])
  (:import [goog History]
           [goog.history EventType]))


;; TODO: Move routing to separate file
(secretary/set-config! :prefix "#")

(defroute "/" []
  (common/set-location "#/feed"))

(defroute "/feed" [query-params]
  (let [query (or (:q query-params) "")]
    (query! query)
    (if @config/config ; Needed when overriding host-url for dev
      (feed/get-feed query (feed!)))
    (state/set-page! :feed)))

(defroute "/peers" []
  (if @config/config
    (peer/get-peers (peers!)))
  (state/set-page! :peers))

(defroute "*" []
  (state/set-page! :error))

(defn get-app-element []
  (gdom/getElement "app"))

(defn on-search [query feed!]
  (common/set-location (str "#feed" (if (empty? query) "" (str "?q=" query)))))

(defn app []
  (fn []
    [:div.app
     (if (state/page-visible? :feed)
       [feed/feed-page (feed!) (personas!) (query!)  #(on-search % (feed!))])
     (if (state/page-visible? :peers)
       [peer/peer-page (peers!) (personas!) (query!) #(on-search % (feed!))])
     (if (state/page-visible? :error)
       [:div.settings "404"])
     [error/error-control @(error!)]]))


(defn mount [el]
  (rdom/render [app] el))

(defn mount-app-element []
  (when-let [el (get-app-element)]
    (mount el)))

;; conditionally start your application based on the presence of an "app" element
;; this is particularly helpful for testing this ns without launching the app
#_(mount-app-element)
(config/get-config #(do (mount-app-element)
                        (persona/get-personas (personas!))
                        (feed/get-feed @(query!) (feed!))
                        #_(peer/get-peers (peers!))) 
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



