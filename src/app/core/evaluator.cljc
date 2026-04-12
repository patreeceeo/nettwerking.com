(ns app.core.evaluator)

(defn success
  ([value]
   {:kind :success
    :value value})
  ([value path]
   {:kind :success
    :value value
    :node-path path}))

(defn partial-result
  ([reason]
   {:kind :partial
    :reason reason})
  ([reason path]
   {:kind :partial
    :reason reason
    :node-path path}))

(defn error-result
  ([reason message]
   {:kind :error
    :reason reason
    :message message})
  ([reason message path]
   {:kind :error
    :reason reason
    :message message
    :node-path path}))

(defn builtin-ref [name]
  {:type :builtin
   :name name})

(def default-builtins
  {"+" {:name "+"
        :min-arity 2
        :validate-args (fn [args]
                         (when-not (every? number? args)
                           {:reason :invalid-argument-type
                            :message "This function needs numeric arguments."}))
        :apply (fn [args]
                 (apply + args))}
   "*" {:name "*"
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

(defn- builtins-entry [builtins name]
  (get builtins name))

(defn- apply-builtin [builtins builtin-name arg-values path]
  (let [{:keys [min-arity max-arity validate-args apply]}
        (builtins-entry builtins builtin-name)]
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

(defn- evaluate-call [node path builtins]
  (let [fn-node (:fn node)
        args (:args node)]
    (cond
      (not (map? fn-node))
      (malformed-node (conj path :fn))

      (not (vector? args))
      (malformed-node (conj path :args))

      :else
      (let [fn-result (evaluate-node fn-node (conj path :fn) builtins)
            arg-results (mapv (fn [index arg]
                                (evaluate-node arg (conj path :args index) builtins))
                              (range (count args))
                              args)
            child-results (into [fn-result] arg-results)]
        (if-let [partial-result (find-result :partial child-results)]
          partial-result
          (if-let [child-error (find-result :error child-results)]
            child-error
            (let [resolved-fn (:value fn-result)]
              (if-not (and (map? resolved-fn)
                           (= :builtin (:type resolved-fn))
                           (string? (:name resolved-fn)))
                (error-result :not-callable "Only built-in functions can be called right now." path)
                (apply-builtin builtins
                               (:name resolved-fn)
                               (mapv :value arg-results)
                               path)))))))))

(defn evaluate-node [node path builtins]
  (if-not (map? node)
    (malformed-node path)
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

          (builtins-entry builtins name)
          (success (builtin-ref name) path)

          :else
          (error-result :unknown-symbol "This function does not exist yet." path)))

      :hole
      (if (string? (:label node))
        (partial-result :incomplete path)
        (malformed-node path))

      :call
      (evaluate-call node path builtins)

      (malformed-node path))))

(defn evaluate
  ([root]
   (evaluate root default-builtins))
  ([root builtins]
   (evaluate-node root [] builtins)))
