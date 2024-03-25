; Copyright 2024 OpenCola
; 
; Licensed under the Apache License, Version 2.0 (the "License");
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
; 
;    http://www.apache.org/licenses/LICENSE-2.0
;
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.

(ns ^:figwheel-hooks opencola.web-ui.theme
  (:require
   [opencola.web-ui.ajax :refer [GET]]
   [opencola.web-ui.model.error :as error]
   [opencola.web-ui.app-state :refer [settings!]]
   [clojure.string :as string]))

(def default-themes [
                        {
                            "name" "Light"
                            "style-attributes" {
                                "--primary-color" "rgb(255, 255, 255)"
                                "--secondary-color" "rgb(239, 239, 239)"
                                "--tertiary-color" "rgb(0, 0, 0)"
                                "--accent-color" "rgb(0, 0, 0)"
                                "--highlight-color" "rgb(255, 0, 0)"
                                "--shadow-color""rgb(232, 232, 232)"
                                "--link-color" "rgb(50, 72, 195)"
                            }
                        }
                        {
                            "name" "Dark"
                            "style-attributes" {
                                "--primary-color" "rgb(12, 11, 11)"
                                "--secondary-color" "rgb(39, 36, 36)"
                                "--tertiary-color" "rgb(114, 114, 114)"
                                "--accent-color" "rgb(221, 221, 221)"
                                "--highlight-color" "rgb(255, 0, 0)"
                                "--shadow-color""rgb(29, 28, 28)"
                                "--link-color" "rgb(51, 94, 204)"
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
    #(do (reset! themes! %) (on-success %))
    #(on-error (error/error-result->str %))
    {:keywords? false}))

(defn theme-names []
  (let [theme-names (map #(get % "name") @themes!)]
    (if (get @(settings!) :debug-mode) (conj theme-names "Default") theme-names)))

(defn get-themes [on-success on-error]
  (when (not @themes!) (load-themes #(on-success %) on-error))
  @themes!)

(defn get-theme-attributes [theme-name]
  (get (first (filter #(= theme-name (get % "name")) @themes!)) "style-attributes"))
