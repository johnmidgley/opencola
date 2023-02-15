(ns opencola.web-ui.common
  (:require
   [goog.string :as gstring]
   [reagent.core :as reagent]
   [markdown-to-hiccup.core :as md2hic]
   [cljsjs.simplemde]
   [opencola.web-ui.model.error :as error]))

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

