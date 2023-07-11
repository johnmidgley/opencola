(ns opencola.web-ui.util)

(defn distinct-by
  ([f coll]
   (let [step (fn step [xs seen]
                (lazy-seq
                 ((fn [[x :as xs] seen]
                    (when-let [s (seq xs)]
                      (let [v (f x)]
                        (if (contains? seen v)
                          (recur (rest s) seen)
                          (cons x (step (rest s) (conj seen v)))))))
                  xs seen)))]
     (step coll #{}))))