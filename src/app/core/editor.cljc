(ns app.core.editor
  (:require [app.core.evaluator :as evaluator]))

(def starter-task
  {:id :starter-sum
   :title "Make (+ 2 3)."
   :description "Finish the starter expression by adding one more number."})

(defn literal-node [value]
  {:type :literal
   :value value})

(defn symbol-node [name]
  {:type :symbol
   :name name})

(defn hole-node
  ([] (hole-node "add value"))
  ([label]
   {:type :hole
    :label label}))

(defn call-node [fn-node args]
  {:type :call
   :fn fn-node
   :args (vec args)})

(def starter-root
  (call-node
   (symbol-node "+")
   [(literal-node 2)
    (hole-node "add value")]))

(defn- valid-node-tree? [node]
  (and (map? node)
       (case (:type node)
         :literal (contains? node :value)
         :symbol (string? (:name node))
         :hole (string? (:label node))
         :call (and (valid-node-tree? (:fn node))
                    (vector? (:args node))
                    (every? valid-node-tree? (:args node)))
         false)))

(defn node-at-path [root path]
  (if (empty? path)
    root
    (get-in root path)))

(defn valid-node-path? [root path]
  (and (vector? path)
       (some? (node-at-path root path))))

(defn- ordered-child-paths [root path]
  (let [node (node-at-path root path)]
    (case (:type node)
      :call
      (into [(conj path :fn)]
            (map (fn [index] (conj path :args index))
                 (range (count (:args node)))))
      [])))

(defn parent-path [path]
  (cond
    (empty? path) nil
    (= :fn (peek path)) (pop path)
    (and (integer? (peek path))
         (>= (count path) 2)
         (= :args (nth path (- (count path) 2))))
    (pop (pop path))
    :else nil))

(defn- sibling-path [root path direction]
  (when-let [parent (parent-path path)]
    (let [siblings (ordered-child-paths root parent)
          current-index (first (keep-indexed (fn [index sibling]
                                               (when (= sibling path)
                                                 index))
                                             siblings))]
      (when (some? current-index)
        (let [offset (case direction
                       :left -1
                       :right 1)]
          (get siblings (+ current-index offset)))))))

(defn- first-child-path [root path]
  (first (ordered-child-paths root path)))

(defn- action [id enabled? & {:keys [reason]}]
  {:id id
   :enabled? enabled?
   :reason reason})

(defn available-actions [{:keys [root selection]}]
  (if-not (valid-node-path? root selection)
    {:selection-valid? false
     :actions [(action :insert-literal false :reason :invalid-selection)
               (action :insert-symbol false :reason :invalid-selection)
               (action :wrap-selected-in-call false :reason :invalid-selection)
               (action :delete-selected false :reason :invalid-selection)
               (action :move-selection-parent false :reason :invalid-selection)
               (action :move-selection-first-child false :reason :invalid-selection)
               (action :move-selection-left-sibling false :reason :invalid-selection)
               (action :move-selection-right-sibling false :reason :invalid-selection)]}
    (let [selected-node (node-at-path root selection)
          hole-selected? (= :hole (:type selected-node))]
      {:selection-valid? true
       :actions [(action :insert-literal true)
                 (action :insert-symbol true)
                 (action :wrap-selected-in-call (not hole-selected?)
                         :reason (when hole-selected? :hole-selected))
                 (action :delete-selected (not hole-selected?)
                         :reason (when hole-selected? :already-hole))
                 (action :move-selection-parent (boolean (parent-path selection))
                         :reason (when-not (parent-path selection) :no-parent))
                 (action :move-selection-first-child (boolean (first-child-path root selection))
                         :reason (when-not (first-child-path root selection) :no-children))
                 (action :move-selection-left-sibling (boolean (sibling-path root selection :left))
                         :reason (when-not (sibling-path root selection :left) :no-left-sibling))
                 (action :move-selection-right-sibling (boolean (sibling-path root selection :right))
                         :reason (when-not (sibling-path root selection :right) :no-right-sibling))]})))

(defn- derive-status
  ([evaluation]
   (derive-status evaluation nil))
  ([evaluation override]
   (if override
     override
     (case (:kind evaluation)
       :partial {:kind :partial
                 :reason (or (:reason evaluation) :incomplete)}
       :error {:kind :error
               :reason (or (:reason evaluation) :evaluation-error)}
       {:kind :success
        :reason :evaluated}))))

(defn- build-state [root selection {:keys [status storage-kind]}]
  (let [normalized-selection (if (valid-node-path? root selection) selection [])
        evaluation (evaluator/evaluate root)
        state {:task starter-task
               :root root
               :selection normalized-selection
               :eval evaluation
               :status (derive-status evaluation status)
               :storage {:kind storage-kind}}]
    (assoc state :available-actions (available-actions state))))

(defn initial-state []
  (build-state starter-root
               [:args 1]
               {:status {:kind :first-run
                         :reason :starter-task}
                :storage-kind :fresh}))

(defn restore-state [snapshot]
  (cond
    (nil? snapshot)
    (initial-state)

    (not (map? snapshot))
    (build-state starter-root
                 [:args 1]
                 {:status {:kind :error
                           :reason :invalid-saved-state}
                  :storage-kind :recovered})

    :else
    (let [root (:root snapshot)
          selection (or (:selection snapshot) [])]
      (cond
        (not (valid-node-tree? root))
        (build-state starter-root
                     [:args 1]
                     {:status {:kind :error
                               :reason :invalid-saved-state}
                      :storage-kind :recovered})

        (not (vector? selection))
        (build-state root
                     []
                     {:status {:kind :error
                               :reason :invalid-saved-selection}
                      :storage-kind :recovered})

        (not (valid-node-path? root selection))
        (build-state root
                     []
                     {:status {:kind :error
                               :reason :invalid-saved-selection}
                      :storage-kind :recovered})

        :else
        (build-state root
                     selection
                     {:status {:kind :restored
                               :reason :session-restored}
                      :storage-kind :restored})))))

(defn- replace-node [root selection replacement]
  (if (empty? selection)
    replacement
    (assoc-in root selection replacement)))

(defn- replacement-node [command]
  (case (:type command)
    :insert-literal (literal-node (:value command))
    :insert-symbol (symbol-node (:name command))
    :replace-selected (:node command)
    nil))

(defn- move-selection-target [root selection direction]
  (case direction
    :parent (parent-path selection)
    :first-child (first-child-path root selection)
    :left-sibling (sibling-path root selection :left)
    :right-sibling (sibling-path root selection :right)
    nil))

(defn- invalid-command-state [state reason]
  (build-state (:root state)
               (:selection state)
               {:status {:kind :error
                         :reason reason}
                :storage-kind (get-in state [:storage :kind])}))

(defn apply-command [state command]
  (let [{:keys [root selection]} state
        command-type (:type command)]
    (cond
      (not (valid-node-path? root selection))
      (build-state root
                   []
                   {:status {:kind :error
                             :reason :invalid-selection}
                    :storage-kind (get-in state [:storage :kind])})

      (= :select command-type)
      (if (valid-node-path? root (:path command))
        (build-state root
                     (:path command)
                     {:storage-kind :dirty})
        (invalid-command-state state :invalid-selection-target))

      (= :move-selection command-type)
      (if-let [target (move-selection-target root selection (:direction command))]
        (build-state root
                     target
                     {:storage-kind :dirty})
        (invalid-command-state state :invalid-move))

      (contains? #{:insert-literal :insert-symbol :replace-selected} command-type)
      (let [replacement (replacement-node command)]
        (cond
          (not (valid-node-tree? replacement))
          (invalid-command-state state :invalid-node)

          :else
          (build-state (replace-node root selection replacement)
                       selection
                       {:storage-kind :dirty})))

      (= :wrap-selected-in-call command-type)
      (let [selected-node (node-at-path root selection)]
        (if (= :hole (:type selected-node))
          (invalid-command-state state :cannot-wrap-hole)
          (build-state (replace-node root
                                     selection
                                     (call-node (symbol-node (or (:fn-name command) "+"))
                                                [selected-node (hole-node "add value")]))
                       (into selection [:args 1])
                       {:storage-kind :dirty})))

      (= :delete-selected command-type)
      (let [selected-node (node-at-path root selection)]
        (if (= :hole (:type selected-node))
          (invalid-command-state state :already-hole)
          (build-state (replace-node root selection (hole-node "add value"))
                       selection
                       {:storage-kind :dirty})))

      :else
      (invalid-command-state state :unknown-command))))
