(ns ^:figwheel-hooks opencola.search-ui
  (:require
   [goog.dom :as gdom]
   [reagent.core :as reagent :refer [atom]]
   [reagent.dom :as rdom]
   [ajax.core :refer [GET POST]] ; https://github.com/JulianBirch/cljs-ajax
   ))

; Good resourcde with secure POST examples
; https://medium.com/pragmatic-programmers/build-the-ui-with-reagent-a2f3757a9176
(println "Loaded.")

(defn multiply [a b] (* a b))

;; define your app data so that it doesn't get over-written on reload
(defonce app-state (atom {}))

(defn get-app-element []
  (gdom/getElement "app"))

(defn results-handler [response]
  (.log js/console (str "Search Response: " response))
  (swap! app-state assoc :results response))

(defn error-handler [{:keys [status status-text]}]
  (.log js/console (str "something bad happened: " status " " status-text)))


(defn search [query]
  (GET "http://localhost:5795/search" {:params {:q query}
                                       :handler results-handler
                                       :response-format :json
                                       :keywords? true
                                       :error-handler error-handler}))



(defn search-box []
  [:div#opencola.search-box>input
   {:type "text"
    :value (:query @app-state)
    :on-change #(swap! app-state assoc :query (-> % .-target .-value))
    :on-keyUp #(if (= (.-key %) "Enter")
                 (search (:query @app-state)))}])


(defn search-header []
  [:div#header.search-header 
   [:img {:src "../img/pull-tab.png" :width 50 :height 50}]
   "openCola"
   (search-box)])

(defn search-result [result]
  ^{:key (:id result)} [:div#search-result.search-result 
                        [:a {:href (str "http://localhost:5795/data/" (:id result))} (:name result)]])

(defn search-results []
  [:div#search-results.search-results 
   (when-let [results (:results @app-state)]
     (for [result (:matches results)]
       (search-result result)))])

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
