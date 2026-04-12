(ns user
  (:require [app.backend.dev :as dev]
            [app.core.editor :as editor]
            [app.core.evaluator :as evaluator]
            [clojure.tools.namespace.repl :as repl]))

(repl/set-refresh-dirs "src" "dev" "test")

(comment
  (editor/initial-state)
  (evaluator/evaluate editor/starter-root)
  (dev/go)
  (dev/reset)
  (dev/stop))
