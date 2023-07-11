(ns ^:figwheel-hooks opencola.web-ui.ajax
  (:require
   [ajax.core :as ajax] ; https://github.com/JulianBirch/cljs-ajax
   [opencola.web-ui.config :as config]))


(defn resolve-service-url [path]
  ;; TODO: Use url construction methods
  (str (config/get-service-url) path))

;; TODO: Abstract common parameters (keywords?, format, response-format)
(defn GET [path success-handler error-handler]
  (ajax/GET 
   (resolve-service-url path)
   {:keywords? true
    :response-format :json
    :handler success-handler
    :error-handler error-handler})) 


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
