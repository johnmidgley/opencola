(ns opencola.web-ui.window)

; Scroll browser to top smoothly
(defn scroll-to-top []
  (js/window.scrollTo
   (clj->js {:top 0 :behavior "smooth"})))