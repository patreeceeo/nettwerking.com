(ns app.test-runner
  (:gen-class)
  (:require [clojure.test :as test]
            [app.backend.server-test]
            [app.core.editor-test]
            [app.core.evaluator-test]))

(defn -main [& _args]
  (let [{:keys [fail error]}
        (test/run-tests 'app.backend.server-test
                        'app.core.editor-test
                        'app.core.evaluator-test)]
    (when (pos? (+ fail error))
      (System/exit 1))))
