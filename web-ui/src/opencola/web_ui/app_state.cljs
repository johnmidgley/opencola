(ns opencola.web-ui.app-state
  (:require
   [reagent.core :as reagent :refer [atom]]
   [opencola.web-ui.common :as common]))

(defonce app-state 
  {
   :page-visible-atoms (apply hash-map (mapcat #(vector % (atom false)) [:feed :peers :error]))
   :query! (atom "")
   :feed! (atom {})
   :peers! (atom {})
   :error! (atom {})
   })

(defn get-page-visible-atoms []
  (:page-visible-atoms app-state))

(defn page-visible? [page]
  @(page (get-page-visible-atoms)))

(defn set-page! [page]
  (let [page-visible-atoms (get-page-visible-atoms)
        page-atom! (page page-visible-atoms)]
    (common/select-atom (map second page-visible-atoms) page-atom!)))

(defn query! 
  ([]
   (:query! app-state))
  ([query]
   (reset! (query!) query) ))

(defn feed! [] 
  (:feed! app-state))

(defn peers! 
  ([]
   (:peers! app-state))
  ([peers]
   (reset! (peers!) peers)))

(defn error! []
  (:error! app-state))