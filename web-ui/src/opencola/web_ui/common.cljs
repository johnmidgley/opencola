(ns opencola.web-ui.common
  (:require
   [goog.string :as gstring]
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

(defn set-location [path]
  (set! (.. js/window -location) path))

(defn action-img [name]
  [:img.action-img {:src (str "../img/" name ".png") :alt name :title name}])

(defn error-message []
  (if-let [e (error/get-error-message)]
    [:div.error e]))


