(ns ^:figwheel-hooks opencola.web-ui.settings
  (:require
   [opencola.web-ui.ajax :refer [GET]]))

(def default-settings {:theme-name "light"})

(defn get-settings [on-success on-error]
  (GET "/storage/settings.json" on-success on-error))