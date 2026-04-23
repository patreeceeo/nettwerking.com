(ns app.frontend.shell-state
  (:require [app.core.editor :as editor]))

(def closed-menu-state
  {:open? false
   :action-index 0})

(def menu-items-by-action-id
  {:insert-literal
   [{:id :insert-literal-3
     :label "Insert 3"
     :summary "Replace the selected node with 3."
     :testid "action-insert-literal-3"
     :command {:type :insert-literal
               :value 3}}
    {:id :insert-literal-4
     :label "Insert 4"
     :summary "Replace the selected node with 4."
     :testid "action-insert-literal-4"
     :command {:type :insert-literal
               :value 4}}]

   :insert-symbol
   [{:id :insert-symbol-plus
     :label "Use +"
     :summary "Replace the selected node with +."
     :testid "action-insert-symbol-plus"
     :command {:type :insert-symbol
               :name "+"}}
    {:id :insert-symbol-wat
     :label "Use wat"
     :summary "Replace the selected node with wat."
     :testid "action-insert-symbol-wat"
     :command {:type :insert-symbol
               :name "wat"}}]

   :wrap-selected
   [{:id :wrap-selected
     :label "Wrap In *"
     :summary "Wrap the selected node in a new form."
     :testid "action-wrap"
     :command {:type :wrap-selected
               :operator-name "*"}}]

   :delete-selected
   [{:id :delete-selected
     :label "Delete Node"
     :summary "Replace the selected node with a hole."
     :testid "action-delete"
     :command {:type :delete-selected}}]})

(defn parent-paths-inclusive [path]
  (loop [current path
         paths ()]
    (if (nil? current)
      (vec paths)
      (recur (editor/parent-path current)
             (conj paths current)))))

(defn breadcrumb-paths [expanded-path]
  (parent-paths-inclusive expanded-path))

(defn stack-child-paths [root expanded-path]
  (mapv (fn [index] (conj expanded-path :args index))
        (range (count (editor/node-args (editor/node-at-path root expanded-path))))))

(defn default-expanded-path [domain-state]
  (let [selection (:selection domain-state)]
    (cond
      (not (editor/valid-node-path? (:root domain-state) selection)) []
      (empty? selection) []
      :else (or (editor/parent-path selection) selection))))

(defn breadcrumb-selection? [expanded-path selection]
  (some #(= selection %) (breadcrumb-paths expanded-path)))

(defn stack-selection? [expanded-path selection]
  (= expanded-path (editor/parent-path selection)))

(defn selection-region [shell-state]
  (let [selection (get-in shell-state [:domain :selection])
        expanded-path (:expanded-path shell-state)]
    (cond
      (breadcrumb-selection? expanded-path selection) :breadcrumb
      (stack-selection? expanded-path selection) :stack
      :else :hidden)))

(defn available-menu-actions [shell-state]
  (->> (get-in shell-state [:domain :available-actions :actions])
       (filter :enabled?)
       (mapcat #(get menu-items-by-action-id (:id %)))
       vec))

(defn current-menu-action [shell-state]
  (nth (available-menu-actions shell-state)
       (get-in shell-state [:menu :action-index] 0)
       nil))

(defn- selection-visible? [domain-state expanded-path stack-open?]
  (let [selection (:selection domain-state)
        root (:root domain-state)]
    (and (editor/valid-node-path? root selection)
         (or (breadcrumb-selection? expanded-path selection)
             (and stack-open?
                  (some #(= selection %) (stack-child-paths root expanded-path)))))))

(defn- normalize-expanded-path [domain-state expanded-path]
  (let [root (:root domain-state)
        candidate (if (editor/valid-node-path? root expanded-path)
                    expanded-path
                    (default-expanded-path domain-state))]
    (if (selection-visible? domain-state candidate true)
      candidate
      (default-expanded-path domain-state))))

(defn- normalize-menu [shell-state]
  (let [actions (available-menu-actions shell-state)
        action-count (count actions)
        open? (and (get-in shell-state [:menu :open?])
                   (pos? action-count))
        max-index (max 0 (dec action-count))
        action-index (min (get-in shell-state [:menu :action-index] 0) max-index)]
    (assoc shell-state :menu {:open? open?
                              :action-index action-index})))

(defn normalize-shell-state [shell-state]
  (let [domain-state (:domain shell-state)
        expanded-path (normalize-expanded-path domain-state (:expanded-path shell-state))
        stack-open? (if (contains? shell-state :stack-open?)
                      (:stack-open? shell-state)
                      true)]
    (-> shell-state
        (assoc :expanded-path expanded-path)
        (assoc :stack-open? stack-open?)
        normalize-menu)))

(defn initial-shell-state
  ([domain-state]
   (initial-shell-state domain-state nil))
  ([domain-state expanded-path]
   (normalize-shell-state {:domain domain-state
                           :expanded-path (or expanded-path (default-expanded-path domain-state))
                           :stack-open? true
                           :menu closed-menu-state})))

(defn state->snapshot [shell-state]
  {:root (get-in shell-state [:domain :root])
   :selection (get-in shell-state [:domain :selection])
   :expanded-path (:expanded-path shell-state)
   :stack-open? (:stack-open? shell-state)})

(defn close-menu [shell-state]
  (assoc shell-state :menu closed-menu-state))

(defn- select-path [domain-state path]
  (editor/apply-command domain-state {:type :select
                                      :path path}))

(defn- select-shell-path [shell-state path]
  (assoc shell-state :domain (select-path (:domain shell-state) path)))

(defn- expandable-node-path? [shell-state path]
  (seq (editor/node-args (editor/node-at-path (get-in shell-state [:domain :root]) path))))

(defn expand-stack-node [shell-state path]
  (if (expandable-node-path? shell-state path)
    (-> (select-shell-path shell-state path)
        (assoc :expanded-path path)
        (assoc :stack-open? true)
        close-menu)
    shell-state))

(defn toggle-breadcrumb-expansion [shell-state path]
  (let [expanded-path (:expanded-path shell-state)
        same-path? (= path expanded-path)
        target-expanded-path (if same-path? expanded-path path)
        target-stack-open? (if same-path?
                             (not (:stack-open? shell-state))
                             true)]
    (-> (select-shell-path shell-state path)
        (assoc :expanded-path target-expanded-path)
        (assoc :stack-open? target-stack-open?)
        close-menu)))

(defn toggle-menu-for-path [shell-state path]
  (let [same-selection? (= path (get-in shell-state [:domain :selection]))
        same-expanded-path? (= path (:expanded-path shell-state))
        breadcrumb-path? (breadcrumb-selection? (:expanded-path shell-state) path)
        shell-state (if same-selection?
                      shell-state
                      (select-shell-path shell-state path))
        open? (and same-selection? (get-in shell-state [:menu :open?]))
        opening? (not open?)]
    (cond-> shell-state
      (and opening? breadcrumb-path? (not same-expanded-path?))
      (assoc :expanded-path path
             :stack-open? true)

      true
      (assoc :menu (assoc closed-menu-state :open? opening?)))))

(defn apply-domain-command [shell-state command]
  (-> shell-state
      (assoc :domain (editor/apply-command (:domain shell-state) command))
      close-menu))

(defn activate-menu-action [shell-state action-id]
  (if-let [{:keys [command]} (first (filter #(= action-id (:id %))
                                            (available-menu-actions shell-state)))]
    (apply-domain-command shell-state command)
    shell-state))

(defn step-menu-selection [shell-state offset]
  (let [actions (available-menu-actions shell-state)
        max-index (max 0 (dec (count actions)))]
    (assoc-in shell-state [:menu :action-index]
              (-> (get-in shell-state [:menu :action-index] 0)
                  (+ offset)
                  (max 0)
                  (min max-index)))))

(defn- breadcrumb-child-path [shell-state]
  (let [selection (get-in shell-state [:domain :selection])
        expanded-path (:expanded-path shell-state)
        crumbs (breadcrumb-paths expanded-path)
        current-index (first (keep-indexed (fn [index path]
                                             (when (= selection path)
                                               index))
                                           crumbs))]
    (cond
      (nil? current-index) nil
      (< current-index (dec (count crumbs))) (nth crumbs (inc current-index))
      (:stack-open? shell-state) (first (stack-child-paths (get-in shell-state [:domain :root]) expanded-path))
      :else nil)))

(defn- stack-move-target [shell-state direction]
  (let [selection (get-in shell-state [:domain :selection])
        siblings (stack-child-paths (get-in shell-state [:domain :root]) (:expanded-path shell-state))
        current-index (first (keep-indexed (fn [index path]
                                             (when (= selection path)
                                               index))
                                           siblings))]
    (case direction
      :up (cond
            (nil? current-index) nil
            (zero? current-index) (:expanded-path shell-state)
            :else (nth siblings (dec current-index)))
      :down (when (and (some? current-index)
                       (< current-index (dec (count siblings))))
              (nth siblings (inc current-index)))
      nil)))

(defn- breadcrumb-move-target [shell-state direction]
  (let [selection (get-in shell-state [:domain :selection])]
    (case direction
      (:up :left) (editor/parent-path selection)
      (:down :right) (breadcrumb-child-path shell-state)
      nil)))

(defn move-selection [shell-state direction]
  (if (get-in shell-state [:menu :open?])
    (case direction
      (:up :left) (step-menu-selection shell-state -1)
      (:down :right) (step-menu-selection shell-state 1)
      shell-state)
    (if-let [target (case (selection-region shell-state)
                      :stack (stack-move-target shell-state direction)
                      :breadcrumb (breadcrumb-move-target shell-state direction)
                      nil)]
      (-> (select-shell-path shell-state target)
          close-menu)
      shell-state)))
