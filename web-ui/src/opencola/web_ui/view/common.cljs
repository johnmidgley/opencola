(ns opencola.web-ui.view.common 
  (:require [clojure.string :as string]
            [goog.string :as gstring]
            [markdown-to-hiccup.core :as md2hic]
            [reagent.core :as reagent]))

(defn item-key []
  (let [new-key (random-uuid)]
    new-key))

(def inline-divider [:span.divider " | "])
(defn keyed-divider [] ^{:key (item-key)} [:span.divider " | "])
(def image-divider [:img.divider {:src "../img/divider.png"}])
(def nbsp (gstring/unescapeEntities "&nbsp;"))

(defn action-img [name]
  [:img.action-img {:src (str "../img/" name ".png") :alt name :title name}])

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
(defn simple-mde [id text state!] 
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
                          :placeholder "Type your post here (or paste a url)..."
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




