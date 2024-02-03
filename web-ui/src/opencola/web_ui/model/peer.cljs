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

(ns opencola.web-ui.model.peer
  (:require
   [opencola.web-ui.ajax :as ajax]
   [opencola.web-ui.model.error :refer [error-result->str]]))

(defn get-peers [persona-id on-success on-error]
  (ajax/GET (str "peers?personaId=" persona-id) 
            #(on-success %)
            #(on-error (error-result->str %))))

(defn update-peer [persona-id peer on-success on-error]
  (ajax/PUT (str "peers?personaId=" persona-id)
            peer
            #(get-peers persona-id on-success on-error)
            #(on-error (error-result->str %))))

(defn delete-peer [persona-id peer on-success on-error]
  (ajax/DELETE 
   (str "peers/" (:id peer) "?personaId=" persona-id)
   #(get-peers persona-id on-success on-error)
   #(on-error (error-result->str %))))

(defn get-invite-token [persona-id on-success on-error]
  (ajax/GET (str "peers/token?personaId=" persona-id)
            #(on-success (:token %))
            #(on-error (error-result->str %))))

(defn token-to-peer [token on-success on-error]
  (ajax/POST "peers/token"
             {:token token}
             on-success
             #(on-error (error-result->str %))))
