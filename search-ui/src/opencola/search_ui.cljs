(ns ^:figwheel-hooks opencola.search-ui
  (:require
   [goog.dom :as gdom]
   [reagent.core :as reagent :refer [atom]]
   [reagent.dom :as rdom]
   [ajax.core :refer [GET POST DELETE]] ; https://github.com/JulianBirch/cljs-ajax
   [cljs-time.coerce :as c]
   [cljs-time.format :as f]
   [lambdaisland.uri :refer [uri]]
   ))

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


(defn feed-handler [response]
  (.log js/console (str "Feed Response: " response))
  (swap! app-state assoc :feed response))


(defn resolve-service-url [path]
  (str (-> @app-state :config :service-url) path))


(defn get-feed [q]
  (GET (resolve-service-url (str "feed" "?q=" q)) {:handler feed-handler
                                                   :response-format :json
                                                   :keywords? true
                                                   :error-handler error-handler}))


(defn search-box []
  [:div.search-box>input
   {:type "text"
    :value (:query @app-state)
    :on-change #(do 
                  (swap! app-state assoc :query (-> % .-target .-value))
                  (if (empty? (-> @app-state :feed :results))
                    (swap! app-state dissoc :feed)))
    :on-keyUp #(if (= (.-key %) "Enter")
                 (let [query (:query @app-state)]
                   (get-feed query)))}])


(defn search-header []
  [:div.search-header 
   [:img {:src "../img/pull-tab.png" :width 50 :height 50}]
   "openCola"
   (search-box)])

(defn search-results []
  [:div.search-results
   (let [query (:query @app-state)]
    (if (and (not (empty? query))
             (= [] (-> @app-state :feed :results)))
      (apply str "No results for '" query  "'")))])

(defn format-time [epoch-second]
  (f/unparse (f/formatter "yyyy-MM-dd hh:mm") (c/from-long (* epoch-second 1000))))

(defn action-img [name]
  [:img.action-img {:src (str "../img/" name ".png")}])

(defn action-item [action value]
  ^{:key action} [:span.action-item (action-img (name action))
                  (if-not value (str value))])

(defn authority-actions [actions]
  [:span.actions-list (for [[action value] actions]
      (action-item action value))])

(defn data-url [item]
  (when-let [dataId (:dataId item)]
    (let [path (str "/data/" dataId)
          host (:host item)]
      (str (if (not= host "") "http://") host path)))) 

(defn data-actions [item]
  (when-let [dataId (:dataId item)]
    [:span.item-link " "
     [:a.action-link {:href (str (data-url item) "/0.html") :target "_blank"} "[Archive]"] " "
     [:a.action-link {:href (data-url item) :target "_blank"} 
      [:img.action-img {:src "../img/download.png" :alt "Download" :title "Download"}]]]))

(defn activities-list [activities]
  [:div.activities-list 
   (for [[idx activity] (map-indexed vector activities)]
     ^{:key (str "activity-" idx)}
     [:div.activity-item (:authorityName activity) " "
      (authority-actions (:actions activity)) " "
      (format-time (:epochSecond activity)) " "
      (data-actions activity)])])


(defn activity [action-counts action-value]
  (when-let [count (action-counts action-value)]
    ^{:key action-value} [:span.activity-item count " " (apply action-item action-value) " " 
                          (name (first action-value)) " "]))


(def display-activities [[:save true] [:like true] [:trust 1.0]])

(defn activities-summary [activities]
(let [action-counts (frequencies (mapcat :actions activities))]  
  [:div.activities-summary
   (filter some? (map #(activity action-counts %) display-activities))]))

(defn delete-handler [entity-id response]
  (swap! app-state update-in [:feed :results] (fn [results] (remove #(= (:entityId %) entity-id) results))))

(defn delete-entity [entity-id]
  (DELETE (resolve-service-url (str "entity/" entity-id)) 
       {:handler (partial delete-handler entity-id)
        :error-handler error-handler}))

(defn delete-control [entity-id]
  [:span.delete-entity {:on-click #(delete-entity entity-id)} (action-img "delete")])

(defn feed-item [item]
  (let [entity-id (:entityId item)
        summary (:summary item)
        item-uri (uri (:uri summary))
        activities (:activities item)]
    ^{:key entity-id} 
    [:div.feed-item
     [:div.item-name 
      [:a.item-link {:href (str item-uri) :target "_blank"} (:name summary)] " "
      (delete-control entity-id)
      [:div.item-host (:host item-uri)]]
     [:div.item-body 
      [:div.item-img-box [:img.item-img {:src (:imageUri summary)}]]
      [:p.item-desc (:description summary)]]
     (activities-list activities)]))

(defn feed []
  (if-let [feed (:feed @app-state)]
    [:div.feed
     (let [results (:results feed)]
       (for [item results]
         (feed-item item)))]))

(defn request-error []
  (if-let [e (:error @app-state)]
    [:div.request-error e]))

(defn feed-page []
  [:div#opencola.search-page
   (search-header)
   (request-error)
   (search-results)
   (feed)])


(defn mount [el]
  (rdom/render [feed-page] el))

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

(get-config #(get-feed (:query @app-state)))

