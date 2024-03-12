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

(ns ^:figwheel-hooks opencola.web-ui.ajax
  (:require
   [ajax.core :as ajax] ; https://github.com/JulianBirch/cljs-ajax
   [opencola.web-ui.config :as config]))

;; Functions for making ajax calls to the server

(defn resolve-service-url [path]
  ;; TODO: Use url construction methods
  (str (config/get-service-url) path))

;; TODO: Abstract common parameters (keywords?, format, response-format)
(defn GET 
  ([path success-handler error-handler ajax-parameters]
   (ajax/GET
     (resolve-service-url path)
     (merge {:keywords? true
             :response-format :json
             :handler success-handler
             :error-handler error-handler} ajax-parameters)))
  ([path success-handler error-handler]
   (GET path success-handler error-handler nil))) 


(defn POST [path body success-handler error-handler] 
  (ajax/POST (resolve-service-url path)
        {:params body
         :keywords? true
         :format :json
         :response-format :json
         :handler success-handler
         :error-handler error-handler}))

(defn PUT [path body success-handler error-handler] 
  (ajax/PUT (resolve-service-url path)
        {:params body
         :keywords? true
         :format :json
         :response-format :json
         :handler success-handler
         :error-handler error-handler}))

(defn DELETE [path success-handler error-handler]
  (ajax/DELETE (resolve-service-url path) 
       {:keywords? true
        :format :json
        :response-format :json
        :handler success-handler
        :error-handler error-handler}))


(defn progress-event-to-percentage [e]
  (int (/ (* 100 (.-loaded e)) (.-total e))))

(defn upload-files [path file-list progress-handler success-handler error-handler]
  (let [form-data (js/FormData.)]
    (doseq [file (array-seq file-list)]
      (.append form-data "file" file))
    (ajax/POST (resolve-service-url path)
      {:body form-data
       :keywords? true
       :response-format :json
       :progress-handler #(progress-handler (progress-event-to-percentage %))
       :handler success-handler
       :error-handler error-handler})))
