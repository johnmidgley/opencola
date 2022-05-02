;; This test runner is intended to be run from the command line
(ns opencola.test-runner
  (:require
    ;; require all the namespaces that you want to test
    [opencola.web-ui-test]
    [figwheel.main.testing :refer [run-tests-async]]))

(defn -main [& args]
  (run-tests-async 5000))
