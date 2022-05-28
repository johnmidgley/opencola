(ns opencola.web-ui.common
  #_(:require ))

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


