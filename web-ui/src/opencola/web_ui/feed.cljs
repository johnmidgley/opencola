(ns ^:figwheel-hooks opencola.web-ui.feed 
  (:require
   [clojure.string :as string :refer [lower-case]]
   [goog.dom :as gdom]
   [reagent.core :as reagent :refer [atom]]
   [reagent.dom :as rdom]
   [cljs-time.coerce :as c]
   [cljs-time.format :as f]
   [lambdaisland.uri :refer [uri]]
   [opencola.web-ui.config :as config]
   [opencola.web-ui.ajax :as ajax]))

;; TODO: Look at https://github.com/Day8/re-com

(defonce error-message (atom nil))

(defn error [message]
  (reset! error-message message)
  (.log js/console message))

;; TODO: Clear error on any successful call
(defn error-handler [{:keys [status status-text]}]
  (error (str "Error: " status ": " status-text)))



(defn get-feed [feed q message]
  (ajax/GET (str "feed" "?q=" q) 
            (fn [response]
              (if message
                (reset! message (cond
                                  (empty? q) nil
                                  (empty? (response :results)) (str "No results for '" q "'")
                                  :else (str "Results for '" q "'"))))
              (reset! feed response))
            error-handler))

(defn search-box [feed message]
  (let [query (atom "")]
    (fn []
      [:div.search-box>input
       {:type "text"
        :value @query
        :on-change #(reset! query (-> % .-target .-value))
        :on-keyUp #(if (= (.-key %) "Enter")
                     (get-feed feed @query message))}])))

(defn search-status [message]
  [:div.search-status @message]) 

(defn search-header [feed]
  (let [message (atom nil)]
    (fn []
      [:div.search-header 
       [:img {:src "../img/pull-tab.png" :width 50 :height 50}]
       "openCola"
       [search-box feed message]
       [search-status message]])))


(defn format-time [epoch-second]
  (f/unparse (f/formatter "yyyy-MM-dd hh:mm") (c/from-long (* epoch-second 1000))))

(defn action-img [name]
  [:img.action-img {:src (str "../img/" name ".png") :alt name :title name}])

(defn action-item [action]
  [:span.action-item (action-img (:type action))
   (when-let [value (:value action)] 
     [:pre (str value)])])

(defn authority-actions [actions]
  [:span.actions-list 
   (for [action actions]
      ^{:key action} [action-item action])])



(defn activity [action-counts action-value]
  (when-let [count (action-counts action-value)]
    ^{:key action-value} [:span.activity-item count " " (apply action-item action-value) " " 
                          (name (first action-value)) " "]))


(defn delete-handler [feed entity-id response]
  (swap! feed update-in [:results] (fn [results] (remove #(= (:entityId %) entity-id) results))))

(defn delete-entity [feed entity-id]
  (ajax/DELETE (str "entity/" entity-id) 
               (partial delete-handler feed entity-id)
               error-handler)) 


(defn data-url [host data-id]
  (when data-id
    (str (if (not= host "") "http://") host "/data/" data-id))) 


(defn delete-control [feed entity-id]
  [:span.delete-entity {:on-click #(delete-entity feed entity-id)} (action-img "delete")])


(defn edit-control [editing?]
  [:span.delete-entity {:on-click #(reset! editing? true)} (action-img "edit")])

(defn comment-handler [feed editing? item]
  (let [entity-id (:entityId item)
        updated-feed (update-in 
                      @feed 
                      [:results]
                      #(map (fn [i] (if (= entity-id (:entityId i)) item i)) %))]
    (reset! feed updated-feed))
  (reset! editing? false))

(defn comment-handler-old [feed entity-id editing? response]
  (reset! editing? false))



(defn add-comment [feed entity-id editing? text]
  (ajax/POST (str "entity/" entity-id "/comment") 
             {:text text }
             (partial comment-handler feed editing?)
             error-handler))

(defn comment-control [feed entity-id expanded?]
  (let [text (atom "")]
    (fn []
      (if @expanded?
        [:div.comment
         [:textarea.comment-text-edit {:type "text"
                                       :value @text
                                       :on-change #(reset! text (-> % .-target .-value))}]
        [:button {:on-click #(add-comment feed entity-id expanded? @text)} "Save"]
        [:button {:on-click #(reset! expanded? false)} "Cancel"]]))))

(defn action-summary [name toggle-fn actions]
  [:span {:on-click toggle-fn} (action-img name) " " (count actions)]) 

(defn flatten-activity [item]
  (mapcat
   (fn [activity]
     (let [activity-no-actions (dissoc activity :actions)]
       (map #(merge activity-no-actions %) (:actions activity))))
   (:activities item)))

(defn item-comment [comment-action]
  (let [{authority-name :authorityName epoch-second :epochSecond text :value} comment-action]
      [:div.item-comment 
       [:div.item-comment-text text]
       [:span.item-attribution (str authority-name " " (format-time epoch-second))]]))

(defn item-comments [visible-fn? expanded? comment-actions feed entity-id]
(if (visible-fn?)
 (let [comment-actions (if @expanded? comment-actions (take 3 comment-actions))] 
   (if (or @expanded? (not-empty comment-actions))
     [:div.item-comments
      [:span 
       {:on-click (fn [] (swap! expanded? #(not %)))}
       "Comments "
       [action-img (if @expanded? "collapse" "expand")]]
      [comment-control feed entity-id expanded?] 
      (doall (for [comment-action comment-actions]
               ^{:key comment-action} [item-comment comment-action]))]))))

(defn item-save [save-action]
  (let [{authority-name :authorityName 
         epoch-second :epochSecond 
         data-id :id
         host :host} save-action]
    [:div.item-save 
     [:span.item-attribution (str authority-name " " (format-time epoch-second))] " "
     [:a.action-link  {:href (str (data-url host data-id) "/0.html") :target "_blank"} [action-img "archive"]]
     " | "
     [:a.action-link  {:href (data-url host data-id) :target "_blank"} [action-img "download"]]]))
 

(defn item-saves [expanded? save-actions]
  (if @expanded?
    [:div.item-saves 
     "Saves:"
     (doall (for [save-action save-actions]
              ^{:key save-action} [item-save save-action]))]))

(defn item-tag [tag-action]
  (let [{authority-name :authorityName
         epoch-second :epochSecond} tag-action] 
    [:div.item-tag
     [:span.item-attribution (str authority-name " " (format-time epoch-second))] " " (:value tag-action)]))

(defn item-list [name expanded? actions item-action]
  (if @expanded?
    [(keyword (str "div.item-" (lower-case name)))
     [:div.list-header (str name ":")]
     (doall (for [action actions]
              ^{:key action} [item-action action]))]))


(defn item-like [like-action]
  (let [{authority-name :authorityName 
         epoch-second :epochSecond } like-action]
    [:div.item-like 
     [:span.item-attribution (str authority-name " " (format-time epoch-second))]]))

;; TODO: Templatize this - same for saves and comments
(defn item-likes [expanded? like-actions]
  (if @expanded?
    [:div.item-likes 
     "Likes:"
     (doall (for [like-action like-actions]
              ^{:key like-action} [item-like like-action]))]))


(defn toggle-atom [atoms atom]
  (doall(for [a atoms]
          (if (not= a atom)
            (reset! a false))))
  (swap! atom #(not %)))

(defn item-activities [feed item editing?]
  (let [saves-expanded? (atom false)
        likes-expanded? (atom false)
        tags-expanded? (atom false)
        comments-expanded? (atom false)
        preview-visible-fn? (fn [] (every? #(not @%) [saves-expanded? likes-expanded? tags-expanded?]))
        toggle (partial toggle-atom [saves-expanded? likes-expanded? tags-expanded? comments-expanded?])] 
    (fn [] 
      (let [entity-id (:entityId item)
            actions-by-type (group-by #(keyword (:type %)) (flatten-activity item))]  
        [:div.activities-summary
         [action-summary "save" (partial toggle saves-expanded?) (:save actions-by-type)]
         [:span.divider " | "]
         [action-summary "like" (partial toggle likes-expanded?) (:like actions-by-type)] 
         [:span.divider " | "]
         [action-summary "comment" (partial toggle comments-expanded?) (:comment actions-by-type)]
         [:span.divider " | "]
         [action-summary "tag" (partial toggle tags-expanded?) (:tag actions-by-type)]
         [:span.divider " | "]
         [delete-control feed entity-id]
         [:span.divider " | "]
         [edit-control editing?]
         [item-list "Tags" tags-expanded? (:tag actions-by-type) item-tag]
         [item-comments preview-visible-fn? comments-expanded? (:comment actions-by-type) feed entity-id]
         [item-saves saves-expanded? (:save actions-by-type)]
         [item-likes likes-expanded? (:like actions-by-type)]]))))


(defn display-feed-item [feed item editing?]
  (let [entity-id (:entityId item)
        summary (:summary item)
        item-uri (uri (:uri summary))
        activities (:activities item)]
    (fn []
      [:div.feed-item
       [:div.item-name 
        [:a.item-link {:href (str item-uri) :target "_blank"} (:name summary)]
        [:div.item-host (:host item-uri)]]
       [:div.item-body 
        [:div.item-img-box [:img.item-img {:src (:imageUri summary)}]]
        [:p.item-desc (:description summary)]]
       [item-activities feed item editing?]])))


(defn update-item-handler [feed editing? item response]
  (let [entity-id (:entityId response)
        updated-feed (update-in 
                      @feed 
                      [:results]
                      #(map (fn [i] (if (= entity-id (:entityId i)) response i)) %))]
    (reset! feed updated-feed))
  (reset! editing? false))


(defn update-item-error-handler [editing? response]
  (error-handler response)
  (reset! editing? false))

(defn update-entity [feed editing? item] 
  (ajax/POST 
   (str "/entity/" (:entityId item))
   item
   (partial update-item-handler feed editing? item)
   (partial update-item-error-handler editing?)))


(defn authority-actions-of-type [authority-id type item]
  (->> item 
       flatten-activity
       (group-by #(keyword (:type %)))
       type
       (filter #(= authority-id (:authorityId %)))))

(defn tags-as-string [authority-id item]
  (string/join " " (map :value (authority-actions-of-type authority-id :tag item))))

;; TODO - Use https://clj-commons.org/camel-snake-kebab/
(defn edit-item [authority-id item]
  (let [summary (:summary item)
        tags (group-by #(keyword (:type %)) (flatten-activity item))] 
    {:entityId (:entityId item)
     :name (:name summary)
     :imageUri (:imageUri summary)
     :description (:description summary)
     :tags (tags-as-string authority-id item)}))

;; TODO: Use keys to get 
(defn edit-feed-item [feed item editing?]
  (let [entity-id (:entityId item)
        summary (:summary item)
        item-uri (uri (:uri summary))
        edit-item (atom (edit-item (:authorityId @feed) item))]
    (fn []
      (println edit-item)
      [:div.feed-item
       [:div.item-name
        [:div.field-header "Name:"]
        [:input.item-link
         {:type "text"
          :value (:name @edit-item)
          :on-change #(swap! edit-item assoc-in [:name] (-> % .-target .-value))}]]
       [:div.item-body 
        [:div.item-img-box 
         [:img.item-img {:src (:imageUri summary)}]]
        [:div.item-image-url 
         [:div.field-header "Image URL:"]
         [:input.item-img-url
          {:type "text"
           :value (:imageUri @edit-item)
           :on-change #(swap! edit-item assoc-in [:imageUri] (-> % .-target .-value))}]]
        [:div.field-header "Description:"]
        [:p.item-desc [:textarea.item-desc-edit
                       {:type "text"
                        :value (:description @edit-item)
                        :on-change #(swap! edit-item assoc-in [:description] (-> % .-target .-value))}]]
        [:div.item-image-url 
         [:div.field-header "Tags:"]
         [:input.item-img-url
          {:type "text"
           :value (:tags @edit-item)
           :on-change #(swap! edit-item assoc-in [:tags] (-> % .-target .-value))}]]
        [:button {:on-click #(update-entity feed editing? @edit-item)} "Save"] " "
        [:button {:on-click #(reset! editing? false)} "Cancel"]]])))



(defn feed-item [feed item]
  (let [editing? (atom false)]
    (fn []
      (if @editing? [edit-feed-item feed item editing?] [display-feed-item feed item editing?]))))


(defn feed-list [feed]
  (if @feed
    [:div.feed
     (let [results (:results @feed)]
       (doall (for [item results]
                ^{:key item} [feed-item feed item])))]))

(defn request-error []
  (if-let [e @error-message]
    [:div.error e]))

(def feed (atom {}))

(defn feed-page []
  (let [feed_ (atom {})]
      (get-feed feed "" nil)
    (fn []
      [:div#opencola.search-page
       [search-header feed]
       [request-error]
       [feed-list feed]])))
