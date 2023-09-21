(ns opencola.web-ui.view.common 
  (:require [clojure.string :as string]
            [goog.string :as gstring]
            [markdown-to-hiccup.core :as md2hic]
            [reagent.core :as reagent]
            [opencola.web-ui.location :as location]))

(defn error-control [e!]
  (when @e!
    [:div.error [:p @e!]]))

(defn item-key []
  (let [new-key (random-uuid)]
    new-key))

(def inline-divider [:span.divider " | "])
(defn keyed-divider [] ^{:key (item-key)} [:span.divider " | "])
(def image-divider [:img.divider {:src "../img/divider.png"}])
(def nbsp (gstring/unescapeEntities "&nbsp;"))

(defn img [name class]
  [:img {:class class :src (str "../img/" name ".png") :alt name :title name}])

(defn action-img [name]
  [img name "action-img"])

(defn input-text [item! key editing?]
  [:input.input-text
   {:type "text"
    :disabled (not editing?)
    :value (key @item!)
    :on-change #(swap! item! assoc-in [key] (-> % .-target .-value))}])

(defn input-checkbox [item! key editing?]
  [:input
   {:type "checkbox"
    :disabled (not editing?)
    :checked (key @item!)
    :on-change #(swap! item! assoc-in [key] (-> % .-target .-checked))}])

;; https://github.com/reagent-project/reagent/blob/master/doc/CreatingReagentComponents.md
(defn simple-mde [id placeholder text state!] 
  (fn [] 
    (reagent/create-class           
     {:display-name  "my-component"
      
      :component-did-mount         
      (fn [_] 
        ;; super ugly. Since SimpleMDE is a non-react, js component, we need some way to be able to 
        ;; get the text out. We do this by storing the object in an atom that can be accessed outside
        ;; of the react control. Might be better to just put a proxy object in the state. Not sure
        ;; if there's a cleaner way to do this. 
        (reset! state! (js/SimpleMDE. 
                        (clj->js 
                         {:element (js/document.getElementById id)
                          :forceSync true
                          :placeholder placeholder
                          :autofocus true
                          :spellChecker false
                          :status false
                          }))))
      
      :reagent-render 
      (fn []
        [:textarea {:id id :value text :on-change #()}])})))

(defn md->component [attributes md-text]
  (let [hiccup (->> md-text (md2hic/md->hiccup) (md2hic/component))]
    (assoc hiccup 1 attributes)))

(defn hidden-file-input [id on-change]
  [:input {:type "file"
           :id id
           :multiple true
           :style {:display "none"}
           :on-change #(on-change (.. % -target -files))}])

(defn select-files-control [content on-change]
  (let [input-id (str (random-uuid))]
    [:span {:on-click #(.click (js/document.getElementById input-id))}
     [:input {:type "file"
              :id input-id
              :multiple true
              :style {:display "none"}
              :on-change #(on-change (.. % -target -files))}]
     content]))

(defn extenstion [filename]
  (let [parts (string/split filename #"\.")]
    (last parts)))

(defn image? [filename]
  (contains? #{"jpg" "jpeg" "png" "gif"} (extenstion filename)))

(defn text-input [text on-change]
  (let [edit-text! (atom text)]
    [:input.text-input
     {:type "text"
      :value @edit-text!
      :on-change (fn [e]
                   (let [val (-> e .-target .-value)]
                     (reset! edit-text! val)
                     (on-change val)))}]))

(defn text-area [text on-change]
  (let [edit-text! (atom text)]
    [:textarea.text-area
     {:type "text"
      :value @edit-text!
      :on-change (fn [e]
                   (let [val (-> e .-target .-value)]
                     (reset! edit-text! val)
                     (on-change val)))}]))

(defn progress-bar [visible?! progress!]
  [:progress {:class "attach-progress" :hidden (not @visible?!) :value @progress! :max 100}])

(defn upload-progress [visible?! progress!]
  (when @visible?!
  [:span
   "Uploading: "
   [progress-bar visible?! progress!]
   (when (= @progress! 100) " processing...")]))


(defn help-control []
  [:a {:href "help/help.html" :target "_blank"} [:img.header-icon {:src  "../img/help.png" :on-click #(location/set-page! :peers)}]])