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

(ns ^:figwheel-hooks opencola.web-ui.config
  (:require
   [ajax.core :refer [GET]]
   [opencola.web-ui.model.error :as error]))

(defonce config (atom nil))

(defn get-config [on-success on-error]
  (GET "config.json" {:handler #(do (reset! config %)
                                    (on-success %))
                      :response-format :json
                      :keywords? true
                      :error-handler #(on-error (error/error-result->str %))}))

(defn get-service-url [] (@config :service-url))




