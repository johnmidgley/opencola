(ns ^:figwheel-hooks opencola.search-ui
  (:require
   [goog.dom :as gdom]
   [reagent.core :as reagent :refer [atom]]
   [reagent.dom :as rdom]
   [ajax.core :refer [GET POST]] ; https://github.com/JulianBirch/cljs-ajax
   ))

(println "Loaded.")

(defn multiply [a b] (* a b))

;; define your app data so that it doesn't get over-written on reload
(defonce app-state (atom {:query "query"}))

(defn get-app-element []
  (gdom/getElement "app"))

(defn results-handler [response]
  (.log js/console (str response))
  (print response)
  (print (type response))
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
    :on-change #(swap! app-state assoc :query (-> % .-target .-value))}])


(defn search-header []
  [:div#opencola.search-header "Search Header"
   (search-box)])

(defn search-result [result]
  ^{:key (:id result)} [:div#search-result.search-result 
       (:name result)])

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
