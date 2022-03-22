(ns ^:figwheel-hooks opencola.search-ui
  (:require
   [goog.dom :as gdom]
   [reagent.core :as reagent :refer [atom]]
   [reagent.dom :as rdom]
   [ajax.core :refer [GET POST]] ; https://github.com/JulianBirch/cljs-ajax
   [cljs-time.coerce :as c]
   [cljs-time.format :as f]
   ))

; Good resourcde with secure POST examples
; https://medium.com/pragmatic-programmers/build-the-ui-with-reagent-a2f3757a9176
(println "Loaded.")

(defn multiply [a b] (* a b))

;; define your app data so that it doesn't get over-written on reload
(defonce app-state (atom {}))

(defn reset-state [] 
  (swap! app-state assoc :error nil)
  (swap! app-state assoc :results nil)
  (swap! app-state assoc :feed nil))

(defn get-app-element []
  (gdom/getElement "app"))

(defn error-handler [{:keys [status status-text]}]
  (swap! app-state assoc :error status-text)
  (.log js/console (str "Error: " status " " status-text)))


(defn config-handler [on-complete-fn response]
  (swap! app-state assoc :config response)
  (on-complete-fn))

(defn get-config [on-complete-fn]
  (GET "config.json" {:handler (partial config-handler on-complete-fn)
                      :response-format :json
                      :keywords? true
                      :error-handler error-handler}))

(defn results-handler [response]
  (.log js/console (str "Search Response: " response))
  (swap! app-state assoc :results response))

(defn feed-handler [response]
  (.log js/console (str "Feed Response: " response))
  (swap! app-state assoc :feed response))


(defn resolve-service-url [path]
  (str (-> @app-state :config :service-url) path))


(defn search [query]
  (reset-state)
  (GET (resolve-service-url "search") {:params {:q query}
                                       :handler results-handler
                                       :response-format :json
                                       :keywords? true
                                       :error-handler error-handler}))

(defn get-feed []
  (reset-state)
  (GET (resolve-service-url "feed") {:handler feed-handler
                                     :response-format :json
                                     :keywords? true
                                     :error-handler error-handler}))


(defn search-box []
  [:div#opencola.search-box>input
   {:type "text"
    :value (:query @app-state)
    :on-change #(swap! app-state assoc :query (-> % .-target .-value))
    :on-keyUp #(if (= (.-key %) "Enter")
                 (let [query (:query @app-state)]
                   (if (or (not query) (= query "")) 
                     (get-feed)
                     (search query))))}])


(defn search-header []
  [:div#header.search-header 
   [:img {:src "../img/pull-tab.png" :width 50 :height 50}]
   "openCola"
   (search-box)])

(defn search-result [result]
  ^{:key (:id result)} [:div#search-result.search-result 
                        [:a {:href (:uri result) :target "_blank"} (:name result)]
                        " "
                        [:a {:href (resolve-service-url (str "data/" (:id result) "/0.html"))
                             :target "blank_"} 
                         "archive"]
                        " "
                        [:a {:href (resolve-service-url (str "data/" (:id result)))
                             :target "blank_"} 
                         "download"]])

(defn search-results []
  [:div#search-results.search-results 
   (doall 
    (if-let [results (:results @app-state)]
      (let [matches (:matches results)]
        (if (empty? matches) 
          (apply str "No results for '" (:query results) "'")
          (for [result matches]
            (search-result result))))))])


(defn format-time [epoch-second]
  (f/unparse (f/formatter "yyyy-MM-dd hh:mm") (c/from-long (* epoch-second 1000))))


(defn action-item [action value]
  ^{:key action} [:span [:img {:src (str "../img/" (name action) ".png") 
                    :width 15 
                    :height 15
                    :border 0
                    :margin 0}] 
       (if-not value (str value))])

(defn actions-list [actions]
  (for [[action value] actions]
    (action-item action value)))


(defn activities-list [activities]
  (for [[idx activity] (map-indexed vector activities)]
    ^{:key (str "activity-" idx)}
    [:div (:authorityName activity) " "
     (actions-list (:actions activity)) " "
     (format-time (:epochSecond activity))]))

(defn feed-item [item]
  ^{:key (:entityId item)} 
  [:div.feed-item 
   (let [summary (:summary item)
         activities (:activities item)]
     [:div.name [:a {:href (:uri summary) :target "_blank"} (:name summary)]
      [:div [:img.image {:src (:imageUri summary)}]
       [:p.description (:description summary)]]
      [:div (activities-list activities)]])])

(defn feed []
  (if-let [feed (:feed @app-state)]
    [:div#feed.feed
     (let [results (:results feed)]
       (for [item results]
         (feed-item item)))]))

(defn request-error []
  (if-let [e (:error @app-state)]
    [:div#request-error.search-error e]))

(defn search-page []
  [:div#opencola.search-page
   (search-header)
   (search-results)
   (feed)
   (request-error)])


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

(get-config #(get-feed))

