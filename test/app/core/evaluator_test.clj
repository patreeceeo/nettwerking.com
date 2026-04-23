(ns app.core.evaluator-test
  (:require [app.core.editor :as editor]
            [app.core.evaluator :as evaluator]
            [clojure.test :refer [deftest is testing]]))

(deftest evaluates-literals-and-builtins
  (testing "a literal evaluates successfully"
    (is (= {:kind :success
            :value 2
            :node-path []}
           (evaluator/evaluate (editor/literal-node 2)))))
  (testing "a built-in symbol resolves successfully"
    (is (= {:kind :success
            :value {:type :builtin :name "+"}
            :node-path []}
           (evaluator/evaluate (editor/symbol-node "+"))))))

(deftest evaluates-nested-forms-eagerly
  (let [expression (editor/call-node
                    '*
                    [(editor/literal-node 2)
                     (editor/call-node
                      '+
                      [(editor/literal-node 3)
                       (editor/literal-node 4)])])]
    (is (= {:kind :success
            :value 14
            :node-path []}
           (evaluator/evaluate expression)))))

(deftest returns-partial-when-a-hole-is-present
  (let [expression (editor/call-node
                    '+
                    [(editor/literal-node 2)
                     (editor/hole-node "add value")])]
    (is (= {:kind :partial
            :reason :incomplete
            :node-path [:args 1]}
           (evaluator/evaluate expression)))))

(deftest returns-errors-for-unknown-symbols-arity-types-and-malformed-call-heads
  (testing "unknown symbols return structured errors"
    (is (= {:kind :error
            :reason :unknown-symbol
            :message "This function does not exist yet."
            :node-path []}
           (evaluator/evaluate (editor/symbol-node "wat")))))
  (testing "wrong arity returns a structured error"
    (is (= {:kind :error
            :reason :wrong-arity
            :message "This function needs more arguments."
            :node-path []}
           (evaluator/evaluate
            (editor/call-node
             '+
             [(editor/literal-node 2)])))))
  (testing "invalid types return a structured error"
    (is (= {:kind :error
            :reason :invalid-argument-type
            :message "This function needs numeric arguments."
            :node-path []}
           (evaluator/evaluate
            (editor/call-node
             '+
             [(editor/literal-node 2)
              (editor/literal-node "three")])))))
  (testing "non-symbol call heads fail as malformed nodes"
    (is (= {:kind :error
            :reason :malformed-node
            :message "This expression node is malformed."
            :node-path [:fn]}
           (evaluator/evaluate
            (editor/call-node
             "wat"
             [(editor/literal-node 3)]))))))

(deftest returns-errors-for-malformed-nodes
  (testing "malformed leaves fail"
    (is (= {:kind :error
            :reason :malformed-node
            :message "This expression node is malformed."
            :node-path []}
           (evaluator/evaluate {:type :literal}))))
  (testing "malformed args fail at the args path"
    (is (= {:kind :error
            :reason :malformed-node
            :message "This expression node is malformed."
            :node-path [:fn]}
           (evaluator/evaluate {:type :call
                                :fn "+"
                                :args {:bad true}})))))

(deftest never-leaks-raw-exceptions
  (let [throwing-builtins {'boom {:name "boom"
                                  :min-arity 0
                                  :apply (fn [_args]
                                           (throw (ex-info "kaboom" {})))}}]
    (is (= {:kind :error
            :reason :apply-failed
            :message "This expression could not be evaluated."
            :node-path []}
           (evaluator/evaluate
            (editor/call-node
             'boom
             [])
            throwing-builtins)))))

(deftest catches-throwing-validators
  (let [throwing-builtins {'boom {:name "boom"
                                  :min-arity 1
                                  :validate-args (fn [_args]
                                                   (throw (ex-info "validator kaboom" {})))
                                  :apply (fn [_args]
                                           :unreachable)}}]
    (is (= {:kind :error
            :reason :validate-failed
            :message "validator kaboom"
            :node-path []}
           (evaluator/evaluate
            (editor/call-node
             'boom
             [(editor/literal-node 1)])
            throwing-builtins)))))
