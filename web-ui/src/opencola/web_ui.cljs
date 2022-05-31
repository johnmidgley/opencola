(ns ^:figwheel-hooks opencola.web-ui
  (:require
   [goog.dom :as gdom]
   [reagent.core :as reagent :refer [atom]]
   [reagent.dom :as rdom]
   [opencola.web-ui.config :as config]
   [opencola.web-ui.common :as common]
   [opencola.web-ui.view.feed :as feed]
   [opencola.web-ui.view.settings :as settings]
   [opencola.web-ui.model.feed :as model]
   [opencola.web-ui.model.settings :as settings-model]
   [opencola.web-ui.model.error :as error]
   [secretary.core :as secretary :refer-macros [defroute]]
   [goog.events :as events])
  (:import [goog History]
           [goog.history EventType]))

(def page-visible-atoms (apply hash-map (mapcat #(vector % (atom false)) [:feed :settings :error])))
(def query! (atom ""))
(def feed! (atom {}))
(def peers! (atom {}))

(defn page-visible? [page]
  @(page page-visible-atoms))

(defn set-page [page]
  (let [page-atom! (page page-visible-atoms)]
    (common/select-atom (map second page-visible-atoms) page-atom!)))

(secretary/set-config! :prefix "#")

(defroute "/" []
  (common/set-location  "#/feed"))

(defroute "/feed" [query-params]
  (let [query (or (:q query-params) "")]
    (reset! query! query)
    (if @config/config ; Needed when overriding host-url for dev
      (model/get-feed query feed!))
    (set-page :feed)))

(defroute "/settings" []
  (if @config/config
    (settings-model/get-peers peers!))
  (set-page :settings))

(defroute "*" []
  (set-page :error))

(defn get-app-element []
  (gdom/getElement "app"))


(defn on-search [query feed!]
  (common/set-location (str "#feed" (if (empty? query) "" (str "?q=" query)))))

(defn app []
  (fn [] [:div.app
          (if @(:feed page-visible-atoms)
            [feed/feed-page feed! query! #(on-search % feed!)])
          (if @(:settings page-visible-atoms)
            [settings/settings-page query! #(on-search % feed!) peers!])
          (if @(:error page-visible-atoms)
            [:div.settings "404"])]))


(defn mount [el]
  (rdom/render [app] el))

(defn mount-app-element []
  (when-let [el (get-app-element)]
    (mount el)))

;; conditionally start your application based on the presence of an "app" element
;; this is particularly helpful for testing this ns without launching the app
#_(mount-app-element)
(config/get-config #(do (mount-app-element)
                        ;; TODO: Figure out a better way to do this. Maybe handle in the AJAX layer?
                        (model/get-feed @query! feed!)
                        (settings-model/get-peers peers!)) 
                   error/error-handler)

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



