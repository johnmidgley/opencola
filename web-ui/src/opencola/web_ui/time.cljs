(ns opencola.web-ui.time 
  (:require
   [cljs-time.coerce :as c]
   [cljs-time.format :as f]))

(defn timezone-to-offset-seconds [[sign hours minutes seconds]]
  (* (if (= sign :-) -1 1) (+ (* hours 3600) (* minutes 60) seconds)))

(def timezone-offset-seconds (timezone-to-offset-seconds (:offset (cljs-time.core/default-time-zone))))

(defn format-time [epoch-second]
  (f/unparse 
   (f/formatter "yyyy-MM-dd hh:mm A") 
   (c/from-long (* (+ epoch-second timezone-offset-seconds) 1000))))