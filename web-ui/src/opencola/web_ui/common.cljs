(ns opencola.web-ui.common
  (:require
   [goog.string :as gstring]
   [reagent.core :as reagent]
   [markdown-to-hiccup.core :as md2hic]
   [cljsjs.simplemde]
   [opencola.web-ui.model.error :as error]))

(def inline-divider [:span.divider " | "])
(def image-divider [:img.divider {:src "../img/divider.png"}])
(def nbsp (gstring/unescapeEntities "&nbsp;"))

(defn toggle-atom [atoms! atom!]
  (doall(for [a! atoms!]
          (if (not= a! atom!)
            (reset! a! false))))
  (swap! atom! #(not %)))

(defn select-atom [atoms! atom!]
  (doall(for [a! atoms!]
          (if (not= a! atom!)
            (reset! a! false))))
  (reset! atom! true))

(defn action-img [name]
  [:img.action-img {:src (str "../img/" name ".png") :alt name :title name}])

;; https://github.com/reagent-project/reagent/blob/master/doc/CreatingReagentComponents.md
(defn simple-mde [id text state!] 
  (fn [] 
    (reagent/create-class           
     {:display-name  "my-component"
      
      :component-did-mount         
      (fn [this] 
        ;; super ugly. Since SimpleMDE is a non-react, js component, we need some way to be able to 
        ;; get the text out. We do this by storing the object in an atom that can be accessed outside
        ;; of the react control. Might be better to just put a proxy object in the state. Not sure
        ;; if there's a cleaner way to do this. 
        (reset! state! (js/SimpleMDE. 
                        (clj->js 
                         {:element (js/document.getElementById id)
                          :forceSync true
                          ; :autofocus true
                          :spellChecker false
                          :status false
                          }))))
      
      :reagent-render 
      (fn []
        [:textarea {:id id :value text :on-change #()}])})))

(defn md->component [attributes md-text]
  (let [hiccup (->> md-text (md2hic/md->hiccup) (md2hic/component))]
    (assoc hiccup 1 attributes)))



