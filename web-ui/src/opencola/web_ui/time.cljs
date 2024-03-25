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

(ns opencola.web-ui.time 
  (:require
   [cljs-time.coerce :as c]
   [cljs-time.format :as f]
   [cljs-time.core :as t]
   [cljs-time.local :refer [local-now to-local-date-time]]))

(defn timezone-to-offset-seconds [[sign hours minutes seconds]]
  (* (if (= sign :-) -1 1) (+ (* hours 3600) (* minutes 60) seconds)))

(def timezone-offset-seconds (timezone-to-offset-seconds (:offset (t/default-time-zone))))

(defn format-time [epoch-second]
  (f/unparse 
   (f/formatter "yyyy-MM-dd hh:mm A") 
   (c/from-long (* (+ epoch-second timezone-offset-seconds) 1000))))

(def seconds-in 
  {:second 1
   :minute 60
   :hour 3600
   :day 86400
   :week 604800
   :month 2419200
   :year 29030400})

(defn prettify-time-interval [seconds unit]
  (let [interval-value (-> seconds (/ (seconds-in unit)) int)]
    (str interval-value " " (name unit) (when (> interval-value 1) "s"))))

(defn pretty-format-time [posted-epoch-second]
  (let [local-epoch-second (int (/ (c/to-long (t/now)) 1000)) 
        difference (- local-epoch-second posted-epoch-second)
        prettify (fn [d u] (str (prettify-time-interval d u) " ago"))]
    (condp >= difference
      (seconds-in :minute) (prettify difference :second)
      (seconds-in :hour) (prettify difference :minute)
      (seconds-in :day) (prettify difference :hour)
      (seconds-in :week) (prettify difference :day)
      (seconds-in :month) (prettify difference :week)
      (seconds-in :year) (prettify difference :month)
      (prettify difference :year))))