(ns ^:figwheel-hooks opencola.web-ui.ajax
  (:require
   [ajax.core :as ajax] ; https://github.com/JulianBirch/cljs-ajax
   ))


(defn resolve-service-url [config path]
  ;; TODO: Use url construction methods
  (str (-> config :service-url) path))


(defn GET [config path success-handler error-handler]
  (ajax/GET 
   (resolve-service-url config path)
   {:handler success-handler
    :response-format :json
    :keywords? true
    :error-handler error-handler})) 


(defn POST [config path body success-handler error-handler] 
  (ajax/POST (resolve-service-url config path)
        {:params body
         :handler success-handler
         :error-handler error-handler
         :format :json}))

(defn DELETE [config path success-handler error-handler]
  (ajax/DELETE (resolve-service-url config path) 
       {:handler success-handler
        :error-handler error-handler}))
