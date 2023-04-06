(ns opencola.web-ui.common
  (:require
   [clojure.string :as string]
   [cljsjs.simplemde]))

(defn toggle-atom [atoms! atom!]
  (doall(for [a! atoms!]
          (when (not= a! atom!)
            (reset! a! false))))
  (swap! atom! #(not %)))

(defn select-atom [atoms! atom!]
  (doall(for [a! atoms!]
          (when (not= a! atom!)
            (reset! a! false))))
  (reset! atom! true))

(defn to-boolean [value]
  (if value
    (case (string/lower-case value)
      "true" true
      "false" false
      value)
    false))

