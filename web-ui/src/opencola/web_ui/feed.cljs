(ns ^:figwheel-hooks opencola.web-ui.feed 
  (:require
   [goog.dom :as gdom]
   [reagent.core :as reagent :refer [atom]]
   [reagent.dom :as rdom]
   [cljs-time.coerce :as c]
   [cljs-time.format :as f]
   [lambdaisland.uri :refer [uri]]
   [opencola.web-ui.config :as config]
   [opencola.web-ui.ajax :as ajax]))

(defonce feed (atom nil))
(defonce error-message (atom nil))

(defn error [message]
  (reset! error-message message)
  (.log js/console message))

;; TODO: Clear error on any successful call
(defn error-handler [{:keys [status status-text]}]
  (error (str "Error: " status ": " status-text)))



(defn get-feed [q message]
  (ajax/GET (str "feed" "?q=" q) 
            (fn [response]
              (.log js/console (str "Feed Response: " response))
              (if message
                (reset! message (cond
                                         (empty? q) nil
                                         (empty? (response :results)) (str "No results for '" q "'")
                                         :else (str "Results for '" q "'"))))
              (reset! feed response))
            error-handler))

(defn search-box [message]
  (let [query (atom "")]
    (fn []
      [:div.search-box>input
       {:type "text"
        :value @query
        :on-change #(reset! query (-> % .-target .-value))
        :on-keyUp #(if (= (.-key %) "Enter")
                     (get-feed @query message))}])))

(defn search-status [message]
  [:div.search-status @message])

(defn search-header []
  (let [message (atom nil)]
    (fn []
      [:div.search-header 
       [:img {:src "../img/pull-tab.png" :width 50 :height 50}]
       "openCola"
       [search-box message]
       [search-status message]])))


(defn format-time [epoch-second]
  (f/unparse (f/formatter "yyyy-MM-dd hh:mm") (c/from-long (* epoch-second 1000))))

(defn action-img [name]
  [:img.action-img {:src (str "../img/" name ".png")}])

(defn action-item [action value]
  [:span.action-item (action-img (name action))
   (if-not value (str value))])

(defn authority-actions [actions]
  [:span.actions-list 
   (for [[action value] actions]
      ^{:key action} [action-item action value])])

(defn data-url [item]
  (when-let [dataId (:dataId item)]
    (let [path (str "/data/" dataId)
          host (:host item)]
      (str (if (not= host "") "http://") host path)))) 

(defn data-actions [item]
  (when-let [dataId (:dataId item)]
    [:span.item-link " "
     [:a.action-link {:href (str (data-url item) "/0.html") :target "_blank"}
      [:img.action-img {:src "../img/archive.png" :alt "View Archive" :title "View Archive"}]] " "
     [:a.action-link {:href (data-url item) :target "_blank"} 
      [:img.action-img {:src "../img/download.png" :alt "Download" :title "Download"}]]]))

(defn activities-list [activities]
  [:div.activities-list 
   (for [[idx activity] (map-indexed vector activities)]
     ^{:key (str "activity-" idx)}
     [:div.activity-item (:authorityName activity) " "
      [authority-actions (:actions activity)] " "
      (format-time (:epochSecond activity)) " "
      [data-actions activity]])])


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
  (swap! feed update-in [:results] (fn [results] (remove #(= (:entityId %) entity-id) results))))

(defn delete-entity [entity-id]
  (ajax/DELETE (str "entity/" entity-id) 
               (partial delete-handler entity-id)
               error-handler)) 


(defn delete-control [entity-id]
  [:span.delete-entity {:on-click #(delete-entity entity-id)} (action-img "delete")])


(defn edit-control [editing?]
  [:span.delete-entity {:on-click #(reset! editing? true)} (action-img "edit")])


(defn display-feed-item [item editing?]
  (let [entity-id (:entityId item)
        summary (:summary item)
        item-uri (uri (:uri summary))
        activities (:activities item)]
    [:div.feed-item
     [:div.item-name 
      [:a.item-link {:href (str item-uri) :target "_blank"} (:name summary)] " "
      [delete-control entity-id]
      [edit-control editing?]
      [:div.item-host (:host item-uri)]]
     [:div.item-body 
      [:div.item-img-box [:img.item-img {:src (:imageUri summary)}]]
      [:p.item-desc (:description summary)]]
     [activities-list activities]]))


(defn update-item-handler [editing? item response]
  (let [entity-id (:entityId item)
        updated-feed (update-in 
                      @feed 
                      [:results]
                      #(map (fn [i] (if (= entity-id (:entityId i)) item i)) %))]
    (reset! feed updated-feed))
  (reset! editing? false))


(defn update-item-error-handler [editing? response]
  (error-handler response)
  (reset! editing? false))

(defn update-entity [editing? item] 
  (ajax/POST 
   (str "/entity/" (:entityId item))
   item
   (partial update-item-handler editing? item)
   (partial update-item-error-handler editing?)))

;; TODO: Use keys to get 
(defn edit-feed-item [item editing?]
  (println "edit-feed-item")
  (let [entity-id (:entityId item)
        summary (:summary item)
        item-uri (uri (:uri summary))
        edit-item (atom item)]
    (fn []
      [:div.feed-item
       [:div.item-name 
        [:input.item-link
         {:type "text"
          :value (-> @edit-item :summary :name)
          :on-change #(swap! edit-item assoc-in [:summary :name] (-> % .-target .-value))}]]
       [:div.item-body 
        [:div.item-img-box 
         [:img.item-img {:src (:imageUri summary)}]]
        [:div.item-image-url 
         [:input.item-img-url
          {:type "text"
           :value (-> @edit-item :summary :imageUri)
           :on-change #(swap! edit-item assoc-in [:summary :imageUri] (-> % .-target .-value))}]]
        [:p.item-desc [:textarea.item-desc-edit
                       {:type "text"
                        :value (-> @edit-item :summary :description)
                        :on-change #(swap! edit-item assoc-in [:summary :description] (-> % .-target .-value))}]]
        [:button {:on-click #(update-entity editing? @edit-item)} "Save"]
        [:button {:on-click #(reset! editing? false)} "Cancel"]]])))



(defn feed-item [item]
  (let [editing? (atom false)]
    (fn []
      (if @editing? [edit-feed-item item editing?] [display-feed-item item editing?]))))


(defn feed-list []
  (if-let [feed @feed]
    [:div.feed
     (let [results (:results feed)]
       (doall (for [item results]
                ^{:key item} [feed-item item])))]))

(defn request-error []
  (if-let [e @error-message]
    [:div.error e]))

(defn feed-page []
  (get-feed "" nil)
  (fn []
    [:div#opencola.search-page
     [search-header]
     [request-error]
     [feed-list]]))
