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

(ns opencola.web-ui.app-state
  (:require
   [reagent.core :as r]
   [opencola.web-ui.ajax :refer [PUT]]
   [opencola.web-ui.common :as common]))

;; Functions to control the state of the application. 

;; TODO: Merge all state into single atom?
(defonce app-state 
  {
   :page-visible-atoms (apply hash-map (mapcat #(vector % (r/atom false)) [:feed :peers :error :personas :settings]))
   :persona! (r/atom nil)
   :personas! (r/atom [])
   :themes! (r/atom [])
   :theme! (r/atom {})
   :query! (r/atom "") 
   :feed! (r/atom {})
   :peers! (r/atom {}) 
   :error! (r/atom "")
   :settings! (r/atom {})
   })

(defn get-page-visible-atoms []
  (:page-visible-atoms app-state))

(defn page-visible? [page]
  @(page (get-page-visible-atoms)))

(defn set-page! [page]
  (let [page-visible-atoms (get-page-visible-atoms)
        page-atom! (page page-visible-atoms)]
    (common/select-atom (map second page-visible-atoms) page-atom!)))

(defn get-page []
   (->> 
    (get-page-visible-atoms)
    (filter #(-> % second deref))
    ffirst))

(defn persona!
  ([]
   (:persona! app-state))
  ([persona]
   (reset! (persona!) persona)))

(defn personas!
  ([]
   (:personas! app-state))
  ([personas] 
   (reset! (personas!) personas)))

(defn query! 
  ([]
   (:query! app-state))
  ([query]
   (reset! (query!) query)))

(defn feed! 
  ([] 
   (:feed! app-state))
  ([feed]
   (reset! (feed!) feed)))

(defn peers! 
  ([]
   (:peers! app-state))
  ([peers]
   (reset! (peers!) peers)))

(defn error!
  ([]
   (:error! app-state))
  ([error]
   (reset! (error!) error)))

(defn settings!
  ([]
   (:settings! app-state))
  ([settings]
   (PUT "/storage/settings.json" settings #() #())
   (reset! (settings!) settings)))

(defn theme!
  ([]
   (:theme! app-state))
  ([theme]
   (settings! (assoc @(settings!) :theme-name theme))
   (reset! (theme!) theme)))

(defn themes!
  ([]
   (:themes! app-state))
  ([themes]
   (reset! (themes!) themes)))
