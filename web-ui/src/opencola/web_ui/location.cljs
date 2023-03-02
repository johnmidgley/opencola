(ns ^:figwheel-hooks opencola.web-ui.location
  (:require
   [opencola.web-ui.app-state :as state :refer [personas! persona! query! feed! peers! error!]]
   [opencola.web-ui.common :as common]))

(defn set-location [path]
  (set! (.. js/window -location) path))

(defn set-state-from-query-params [query-params]
  (let [{persona :p query :q} query-params]
    (query! (or query ""))
    (persona! persona)))

(defn param-to-string [p v]
  (if (not (clojure.string/blank? v))
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
    (set-location (str "#/" page (if (not-empty params) (str "?" params))))))

(defn set-page! [page]
  (state/set-page! page)
  (set-location-from-state))

(defn set-persona! [persona]
  (state/persona! persona)
  (set-location-from-state))
