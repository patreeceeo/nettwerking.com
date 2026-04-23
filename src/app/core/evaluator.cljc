(ns app.core.evaluator)

(defn success
  "Builds a successful evaluation result, optionally annotated with a node path."
  ([value]
   {:kind :success
    :value value})
  ([value path]
   {:kind :success
    :value value
    :node-path path}))

(defn partial-result
  "Builds a partial evaluation result for incomplete expressions."
  ([reason]
   {:kind :partial
    :reason reason})
  ([reason path]
   {:kind :partial
    :reason reason
    :node-path path}))

(defn error-result
  "Builds a structured evaluation error, optionally annotated with a node path."
  ([reason message]
   {:kind :error
    :reason reason
    :message message})
  ([reason message path]
   {:kind :error
    :reason reason
    :message message
    :node-path path}))

(defn builtin-ref
  "Builds the value representation returned when a builtin symbol is resolved."
  [name]
  {:type :builtin
   :name name})

(def default-builtins
  "The builtin function table available to the MVP evaluator."
  {'+ {:name "+"
       :min-arity 2
       :validate-args (fn [args]
                        (when-not (every? number? args)
                          {:reason :invalid-argument-type
                           :message "This function needs numeric arguments."}))
       :apply (fn [args]
                (apply + args))}
   '* {:name "*"
       :min-arity 2
       :validate-args (fn [args]
                        (when-not (every? number? args)
                          {:reason :invalid-argument-type
                           :message "This function needs numeric arguments."}))
       :apply (fn [args]
                (apply * args))}})

(declare evaluate-node)

(defn- malformed-node [path]
  (error-result :malformed-node "This expression node is malformed." path))

(defn- find-result [kind results]
  (first (filter #(= kind (:kind %)) results)))

(defn- builtins-entry [builtins fn-symbol]
  (get builtins fn-symbol))

(defn- apply-builtin [builtins fn-symbol arg-values path]
  (let [{:keys [min-arity max-arity validate-args apply]}
        (builtins-entry builtins fn-symbol)]
    (cond
      (nil? apply)
      (error-result :unknown-symbol "This function does not exist yet." path)

      (< (count arg-values) min-arity)
      (error-result :wrong-arity "This function needs more arguments." path)

      (and max-arity (> (count arg-values) max-arity))
      (error-result :wrong-arity "This function has too many arguments." path)

      :else
      (if-let [{:keys [reason message]} (and validate-args
                                             (validate-args arg-values))]
        (error-result reason message path)
        (try
          (success (apply arg-values) path)
          (catch #?(:clj Throwable :cljs :default) _error
            (error-result :apply-failed "This expression could not be evaluated." path)))))))

(defn- evaluate-leaf [node path builtins]
  (case (:type node)
    :literal
    (if (contains? node :value)
      (success (:value node) path)
      (malformed-node path))

    :symbol
    (let [name (:name node)]
      (cond
        (not (string? name))
        (malformed-node path)

        (builtins-entry builtins (symbol name))
        (success (builtin-ref name) path)

        :else
        (error-result :unknown-symbol "This function does not exist yet." path)))

    :hole
    (if (string? (:label node))
      (partial-result :incomplete path)
      (malformed-node path))

    (malformed-node path)))

(defn- evaluate-call [node path builtins]
  (let [fn-symbol (:fn node)
        args (:args node)]
    (cond
      (not (symbol? fn-symbol))
      (malformed-node (conj path :fn))

      (not (vector? args))
      (malformed-node (conj path :args))

      :else
      (let [arg-results (mapv (fn [index arg]
                                (evaluate-node arg (conj path :args index) builtins))
                              (range (count args))
                              args)
            child-results arg-results]
        (cond
          (find-result :partial child-results)
          (find-result :partial child-results)

          (find-result :error child-results)
          (find-result :error child-results)

          :else
          (if-not (builtins-entry builtins fn-symbol)
            (error-result :unknown-symbol "This function does not exist yet." path)
            (apply-builtin builtins
                           fn-symbol
                           (mapv :value arg-results)
                           path)))))))

(defn evaluate-node
  "Evaluates a single node within a tree using the supplied builtin table."
  [node path builtins]
  (if-not (map? node)
    (malformed-node path)
    (case (:type node)
      :call (evaluate-call node path builtins)
      (evaluate-leaf node path builtins))))

(defn evaluate
  "Evaluates a root expression with either the default or supplied builtins."
  ([root]
   (evaluate root default-builtins))
  ([root builtins]
   (evaluate-node root [] builtins)))
