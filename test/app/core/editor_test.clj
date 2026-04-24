(ns app.core.editor-test
  (:require [app.core.editor :as editor]
            [clojure.test :refer [deftest is testing]]))

(defn- actions-by-id [state]
  (into {}
        (map (juxt :id identity))
        (get-in state [:available-actions :actions])))

(deftest initial-state-uses-the-starter-tree
  (let [state (editor/initial-state)
        selected-node (editor/node-at-path (:root state) (:selection state))
        actions (actions-by-id state)]
    (is (= editor/starter-task (:task state)))
    (is (= :first-run (get-in state [:status :kind])))
    (is (= :fresh (get-in state [:storage :kind])))
    (is (= :partial (get-in state [:eval :kind])))
    (is (= [:args 1] (:selection state)))
    (is (= :hole (:type selected-node)))
    (is (true? (:enabled? (:add-child actions))))
    (is (false? (:enabled? (:wrap-selected actions))))))

(deftest restore-snapshot-handles-valid-and-invalid-snapshots
  (testing "a valid snapshot is restored"
    (let [snapshot {:root (editor/call-node
                           '+
                           [(editor/literal-node 2)
                            (editor/literal-node 3)])
                    :selection [:args 0]}
          state (editor/restore-snapshot snapshot)]
      (is (= :restored (get-in state [:status :kind])))
      (is (= :restored (get-in state [:storage :kind])))
      (is (= [:args 0] (:selection state)))
      (is (= :success (get-in state [:eval :kind])))))
  (testing "an invalid snapshot falls back safely"
    (let [state (editor/restore-snapshot {:root {:type :call
                                                 :fn "+"
                                                 :args {:bad true}}})]
      (is (= :error (get-in state [:status :kind])))
      (is (= :invalid-saved-state (get-in state [:status :reason])))
      (is (= :recovered (get-in state [:storage :kind])))
      (is (= editor/starter-root (:root state)))))
  (testing "an invalid saved selection keeps a valid tree but recovers the selection"
    (let [snapshot {:root (editor/call-node
                           '+
                           [(editor/literal-node 2)
                            (editor/literal-node 3)])
                    :selection [:args 4]}
          state (editor/restore-snapshot snapshot)]
      (is (= :error (get-in state [:status :kind])))
      (is (= :invalid-saved-selection (get-in state [:status :reason])))
      (is (= [] (:selection state)))
      (is (= (:root snapshot) (:root state))))))

(deftest available-actions-reflect-selection-shape
  (testing "holes expose fill actions and disable destructive actions"
    (let [state (editor/initial-state)
          actions (actions-by-id state)]
      (is (true? (get-in state [:available-actions :selection-valid?])))
      (is (true? (:enabled? (:add-child actions))))
      (is (= :hole-selected (:reason (:wrap-selected actions))))
      (is (= :already-hole (:reason (:delete-selected actions))))))
  (testing "literals expose wrap and delete actions"
    (let [state (editor/apply-command (editor/initial-state)
                                      {:type :select
                                       :path [:args 0]})
          actions (actions-by-id state)]
      (is (true? (:enabled? (:wrap-selected actions))))
      (is (true? (:enabled? (:delete-selected actions))))
      (is (true? (:enabled? (:move-selection-parent actions))))))
  (testing "invalid selections return explicit reasons"
    (let [actions (editor/available-actions {:root (:root (editor/initial-state))
                                             :selection [:args 99]})]
      (is (false? (:selection-valid? actions)))
      (is (every? #(= :invalid-selection (:reason %))
                  (:actions actions))))))

(deftest apply-command-rewrites-the-tree-and-recomputes-state
  (testing "filling the starter hole reaches success"
    (let [state (editor/apply-command (editor/initial-state)
                                      {:type :replace-selected
                                       :node (editor/literal-node 3)})]
      (is (= :success (get-in state [:eval :kind])))
      (is (= 5 (get-in state [:eval :value])))
      (is (= :success (get-in state [:status :kind])))
      (is (= :dirty (get-in state [:storage :kind])))))
  (testing "replacing a selected literal rewrites the tree"
    (let [state (-> (editor/initial-state)
                    (editor/apply-command {:type :replace-selected
                                           :node (editor/literal-node 3)})
                    (editor/apply-command {:type :select
                                           :path [:args 0]})
                    (editor/apply-command {:type :replace-selected
                                           :node (editor/literal-node 4)}))]
      (is (= 4 (:value (editor/node-at-path (:root state) [:args 0]))))
      (is (= :success (get-in state [:eval :kind])))
      (is (= 7 (get-in state [:eval :value])))))
  (testing "wrapping a selected node creates a partial form and moves selection"
    (let [state (-> (editor/initial-state)
                    (editor/apply-command {:type :select
                                           :path [:args 0]})
                    (editor/apply-command {:type :wrap-selected
                                           :operator-name "*"}))]
      (is (= :call (:type (editor/node-at-path (:root state) [:args 0]))))
      (is (= '* (get-in state [:root :args 0 :fn])))
      (is (= [:args 0 :args 1] (:selection state)))
      (is (= :partial (get-in state [:eval :kind])))))
  (testing "deleting a subtree replaces it with a hole"
    (let [state (-> (editor/initial-state)
                    (editor/apply-command {:type :select
                                           :path [:args 0]})
                    (editor/apply-command {:type :delete-selected}))]
      (is (= :hole (:type (editor/node-at-path (:root state) [:args 0]))))
      (is (= :partial (get-in state [:eval :kind])))))
  (testing "adding a child to a literal node creates a call node"
    (let [state (-> (editor/initial-state)
                    (editor/apply-command {:type :replace-selected
                                           :node (editor/literal-node 2)})
                    (editor/apply-command {:type :select
                                           :path [:args 0]})
                    (editor/apply-command {:type :add-child}))]
      (is (= :call (:type (editor/node-at-path (:root state) [:args 0]))))
      (is (= '+ (get-in state [:root :args 0 :fn])))
      (is (= [:args 0 :args 1] (:selection state)))
      (is (= :partial (get-in state [:eval :kind])))))
  (testing "adding a child to an existing call node appends an argument"
    (let [state (-> (editor/initial-state)
                    (editor/apply-command {:type :replace-selected
                                           :node (editor/literal-node 3)})
                    (editor/apply-command {:type :add-child}))]
      (is (= 3 (count (get-in state [:root :args]))))
      (is (= :hole (:type (editor/node-at-path (:root state) [:args 2]))))
      (is (= [:args 2] (:selection state)))
      (is (= :partial (get-in state [:eval :kind]))))))

(deftest selection-movement-traverses-the-tree
  (let [state (-> (editor/initial-state)
                  (editor/apply-command {:type :move-selection
                                         :direction :left-sibling})
                  (editor/apply-command {:type :move-selection
                                         :direction :parent})
                  (editor/apply-command {:type :move-selection
                                         :direction :first-child})
                  (editor/apply-command {:type :move-selection
                                         :direction :right-sibling}))]
    (is (= [:args 1] (:selection state)))))

(deftest invalid-commands-fail-explicitly
  (testing "unknown commands return a structured error status"
    (let [state (editor/apply-command (editor/initial-state)
                                      {:type :do-something-else})]
      (is (= :error (get-in state [:status :kind])))
      (is (= :unknown-command (get-in state [:status :reason])))))
  (testing "attempting to wrap a hole fails fast"
    (let [state (editor/apply-command (editor/initial-state)
                                      {:type :wrap-selected})]
      (is (= :error (get-in state [:status :kind])))
      (is (= :cannot-wrap-hole (get-in state [:status :reason]))))))
