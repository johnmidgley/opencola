; Copyright 2024-2026 OpenCola
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

(ns ^:figwheel-hooks opencola.web-ui.location
  (:require
   [opencola.web-ui.app-state :as state :refer [persona! personas! query! feed!]]
   [clojure.string :as string]))

(defn set-location [path]
  (set! (.. js/window -location) path))

(defn set-state-from-query-params [query-params]
  (let [{persona :p query :q} query-params
        q (or query "")] 
    (query! q)
    (persona! persona)))

(defn param-to-string [p v]
  (when (not (string/blank? v))
    (str p "=" v)))

(defn params-from-state []
  (->> [["p" (persona!)] ["q" (query!)]]
       (map (fn [[p a]] (param-to-string p @a)))
       (filter some?)
       (interpose "&")
       (apply str)))

(defn set-location-from-state []
  (let [page (name (state/get-page))
        params (params-from-state)]
    (set-location (str "#/" page (when (not-empty params) (str "?" params))))))

(defn set-page! [page]
  (when (not= :feed page)
    (query! "")
    (feed! {})) 
  (when (and (= :peers page) (not @(persona!)))
    (persona! (-> @(personas!) :items first :id)))
  (state/set-page! page)
  (set-location-from-state))
