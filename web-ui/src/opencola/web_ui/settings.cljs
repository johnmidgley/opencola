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

(ns ^:figwheel-hooks opencola.web-ui.settings
  (:require
   [opencola.web-ui.ajax :refer [GET]]))

(def default-settings {:theme-name "light"
                       :debug-mode false})

(defn get-settings [on-success on-error]
  (GET "/storage/settings.json" on-success on-error))