(ns ^:figwheel-hooks opencola.web-ui.theme
  (:require
   [opencola.web-ui.ajax :refer [GET]]
   [opencola.web-ui.model.error :as error]
   [clojure.string :as string]))

(def default-themes [
                        {
                            "name" "Light"
                            "style-attributes" {
                                "primaryColor" "rgb(255, 255, 255)"
                                "secondaryColor" "rgb(239, 239, 239)"
                                "tertiaryColor" "rgb(0, 0, 0)"
                                "accentColor" "rgb(0, 0, 0)"
                                "highlightColor" "rgb(255, 0, 0)"
                                "shadowColor""rgb(232, 232, 232)"
                                "linkColor" "rgb(50, 72, 195)"
                            }
                        }
                        {
                            "name" "Dark"
                            "style-attributes" {
                                "primaryColor" "rgb(12, 11, 11)"
                                "secondaryColor" "rgb(39, 36, 36)"
                                "tertiaryColor" "rgb(114, 114, 114)"
                                "accentColor" "rgb(221, 221, 221)"
                                "highlightColor" "rgb(255, 0, 0)"
                                "shadowColor""rgb(29, 28, 28)"
                                "linkColor" "rgb(51, 94, 204)"
                            }
                        }
                    ])

(defonce themes! (atom nil))

(defn kebabify [s split-regex]
  (string/lower-case (str "--" (string/join "-" (string/split s split-regex)))))

(defn parse-themes [theme-data]
  (map (fn [theme] (update theme "style-attributes"
                           #(zipmap (map (fn [k] (kebabify k #"(?=[A-Z])")) (keys %)) (vals %)))
         ) theme-data))

(defn load-themes [on-success on-error]
  (GET "/storage/themes.json" 
    #(do (reset! themes! (parse-themes %)) (on-success %))
    #(on-error (error/error-result->str %))
    {:keywords? false}))

(defn theme-names [] 
  (map #(get % "name") @themes!))

(defn get-themes [on-success on-error]
  (when (not @themes!) (load-themes #(on-success %) on-error))
  @themes!)

(defn get-theme-attributes [theme-name]
  (get (first (filter #(= theme-name (get % "name")) @themes!)) "style-attributes"))
