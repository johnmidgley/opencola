(ns ^:figwheel-hooks opencola.web-ui.theme
  (:require
   [opencola.web-ui.ajax :as ajax :refer [GET]]
   [clojure.string :as string]))

(def themes {"dark" {
                    "primaryColor" "rgb(12, 11, 11)"
                    "secondaryColor" "rgb(39, 36, 36)"
                    "tertiaryColor" "rgb(114, 114, 114)"
                    "accentColor" "rgb(221, 221, 221)"
                    "highlightColor" "rgb(255, 0, 0)"
                } 
             "light" {
                    "primaryColor" "rgb(255, 255, 255)"
                    "secondaryColor" "rgb(228, 228, 228)"
                    "tertiaryColor" "rgb(0, 0, 0)"
                    "accentColor" "rgb(0, 0, 0)"
                    "highlightColor" "rgb(255, 0, 0)"
                }
            })

(defn kebabify [s split-regex]
  (string/lower-case (str "--" (string/join "-" (string/split s split-regex)))))

(defn kebabify-key [[k v]]
  [(kebabify k #"(?=[A-Z])") v])

(defn get-themes []
  (reduce-kv (fn [acc k v] (assoc acc k (into {} (map kebabify-key v)))) {} themes))
