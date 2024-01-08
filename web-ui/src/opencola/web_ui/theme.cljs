(ns ^:figwheel-hooks opencola.web-ui.theme
  (:require
   [ajax.core :refer [GET]]
   [opencola.web-ui.model.error :as error]
   [clojure.string :as string]))

(defonce themes! (atom nil))

(defn kebabify [s split-regex]
  (string/lower-case (str "--" (string/join "-" (string/split s split-regex)))))

(defn parse-themes [theme-data]
  (map (fn [theme] (update theme "style-attributes"
                           #(zipmap (map (fn [k] (kebabify k #"(?=[A-Z])")) (keys %)) (vals %)))
         ) theme-data))

(defn load-themes [on-success on-error]
  (GET "themes.json" {:handler #(do (reset! themes! (parse-themes %))
                                    (on-success %))
                      :response-format :json
                      :keywords? false
                      :error-handler #(on-error (error/error-result->str %))}))

(defn theme-names [] 
  (map #(get % "name") @themes!))

(defn get-themes [on-success]
  (when (not @themes!) (load-themes #(on-success %) #()))
  @themes!)

(defn get-theme-attributes [theme-name]
  (get (first (filter #(= theme-name (get % "name")) @themes!)) "style-attributes"))
