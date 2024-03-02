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

(ns ^:figwheel-hooks opencola.web-ui.model.tag
  (:require 
   [opencola.web-ui.model.action :refer [persist-action!]]))

(defn tag-entity! [context persona-id entity-id value on-success on-error]
  (persist-action! 
   context
   persona-id 
   entity-id 
   "tags" 
   {:value value} 
   on-success 
   on-error))