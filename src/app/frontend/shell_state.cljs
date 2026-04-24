(ns app.frontend.shell-state
  (:require [app.core.editor :as editor]
            [cljs.tools.reader.edn :as edn]
            [clojure.string :as string]))

(def closed-menu-state
  "The canonical closed state for the node action menu."
  {:open? false
   :action-index 0})

(def menu-items-by-action-id
  "Frontend-only menu presentation keyed by domain action id."
  {:insert-literal
   [{:id :edit
     :label "Edit"
     :summary "Edit this node in place."
     :testid "action-edit"
     :transition :begin-editing}]

   :insert-symbol
   [{:id :edit
     :label "Edit"
     :summary "Edit this node in place."
     :testid "action-edit"
     :transition :begin-editing}]

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

(defn- editing-node-text [node]
  (case (:type node)
    :literal (str (:value node))
    :symbol (:name node)
    :hole ""
    :call (if-let [fn-symbol (editor/call-fn-symbol node)]
            (name fn-symbol)
            "")
    ""))

(defn- parse-edit-value [text]
  (let [trimmed (string/trim text)]
    (when (seq trimmed)
      (try
        (edn/read-string trimmed)
        (catch :default _error
          ::invalid-edit)))))

(defn- replacement-node-for-edit [node text]
  (let [value (parse-edit-value text)]
    (cond
      (or (nil? value) (= ::invalid-edit value))
      nil

      (= :call (:type node))
      (when (symbol? value)
        (editor/call-node value (editor/node-children node)))

      (symbol? value)
      (editor/symbol-node (name value))

      :else
      (editor/literal-node value))))

(defn parent-paths-inclusive
  "Returns a path plus each of its parents, ordered from root to the path itself."
  [path]
  (loop [current path
         paths ()]
    (if (nil? current)
      (vec paths)
      (recur (editor/parent-path current)
             (conj paths current)))))

(defn breadcrumb-paths
  "Returns the breadcrumb trail for the currently expanded node."
  [expanded-path]
  (parent-paths-inclusive expanded-path))

(defn stack-child-paths
  "Returns the immediate child paths shown in the current one-level stack view."
  [root expanded-path]
  (mapv (fn [index] (conj expanded-path :args index))
        (range (count (editor/node-children (editor/node-at-path root expanded-path))))))

(defn default-expanded-path
  "Chooses the initial expanded node based on the current selection."
  [domain-state]
  (let [selection (:selection domain-state)]
    (cond
      (not (editor/valid-node-path? (:root domain-state) selection)) []
      (empty? selection) []
      :else (or (editor/parent-path selection) selection))))

(defn breadcrumb-selection?
  "True when selection is visible in the breadcrumb trail."
  [expanded-path selection]
  (some #(= selection %) (breadcrumb-paths expanded-path)))

(defn stack-selection?
  "True when selection is one of the immediate children of the expanded node."
  [expanded-path selection]
  (= expanded-path (editor/parent-path selection)))

(defn selection-region
  "Classifies the selected node as breadcrumb, stack, or hidden."
  [shell-state]
  (let [selection (get-in shell-state [:domain :selection])
        expanded-path (:expanded-path shell-state)]
    (cond
      (breadcrumb-selection? expanded-path selection) :breadcrumb
      (stack-selection? expanded-path selection) :stack
      :else :hidden)))

(defn available-menu-actions
  "Expands enabled domain actions into concrete frontend menu items."
  [shell-state]
  (let [enabled-actions (->> (get-in shell-state [:domain :available-actions :actions])
                             (filter :enabled?)
                             (mapcat #(get menu-items-by-action-id (:id %))))]
    (->> enabled-actions
         (reduce (fn [{:keys [seen items]} item]
                   (if (contains? seen (:id item))
                     {:seen seen :items items}
                     {:seen (conj seen (:id item))
                      :items (conj items item)}))
                 {:seen #{}
                  :items []})
         :items
         vec)))

(defn current-menu-action
  "Returns the currently highlighted menu item, if any."
  [shell-state]
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

(defn- normalize-editing [shell-state]
  (if-let [{:keys [path text]} (:editing shell-state)]
    (let [domain-state (:domain shell-state)
          selection (:selection domain-state)]
      (if (and (string? text)
               (= path selection)
               (selection-visible? domain-state
                                   (:expanded-path shell-state)
                                   (:stack-open? shell-state)))
        shell-state
        (dissoc shell-state :editing)))
    shell-state))

(defn normalize-shell-state
  "Repairs shell state after any transition so selection, expansion, and menu state stay coherent."
  [shell-state]
  (let [domain-state (:domain shell-state)
        expanded-path (normalize-expanded-path domain-state (:expanded-path shell-state))
        stack-open? (if (contains? shell-state :stack-open?)
                      (:stack-open? shell-state)
                      true)]
    ;; The shell adds UI-only state on top of the editor domain, so every
    ;; transition is normalized through one path before rendering.
    (-> shell-state
        (assoc :expanded-path expanded-path)
        (assoc :stack-open? stack-open?)
        normalize-menu
        normalize-editing)))

(defn initial-shell-state
  "Builds the UI shell state that wraps an editor domain state."
  ([domain-state]
   (initial-shell-state domain-state nil))
  ([domain-state expanded-path]
   (normalize-shell-state {:domain domain-state
                           :expanded-path (or expanded-path (default-expanded-path domain-state))
                           :stack-open? true
                           :menu closed-menu-state})))

(defn state->snapshot
  "Serializes the persistable portion of shell state."
  [shell-state]
  {:root (get-in shell-state [:domain :root])
   :selection (get-in shell-state [:domain :selection])
   :expanded-path (:expanded-path shell-state)
   :stack-open? (:stack-open? shell-state)})

(defn close-menu
  "Closes the node action menu and resets its highlighted item."
  [shell-state]
  (assoc shell-state :menu closed-menu-state))

(defn begin-editing
  "Starts inline editing for the currently selected node."
  [shell-state]
  (let [path (get-in shell-state [:domain :selection])
        root (get-in shell-state [:domain :root])]
    (if (editor/valid-node-path? root path)
      (-> shell-state
          close-menu
          (assoc :editing {:path path
                           :text (editing-node-text (editor/node-at-path root path))}))
      shell-state)))

(defn update-editing-text
  "Updates the draft text for the active inline editor."
  [shell-state text]
  (if (:editing shell-state)
    (assoc-in shell-state [:editing :text] text)
    shell-state))

(defn cancel-editing
  "Cancels the active inline edit and restores the prior node state."
  [shell-state]
  (dissoc shell-state :editing))

(defn commit-editing
  "Commits the active inline edit when the draft can be parsed into a node."
  [shell-state]
  (if-let [{:keys [path text]} (:editing shell-state)]
    (let [root (get-in shell-state [:domain :root])]
      (if-not (editor/valid-node-path? root path)
        (dissoc shell-state :editing)
        (let [node (editor/node-at-path root path)
              replacement (replacement-node-for-edit node text)]
          (if replacement
            (-> shell-state
                (assoc :domain (-> (:domain shell-state)
                                   (editor/apply-command {:type :select
                                                          :path path})
                                   (editor/apply-command {:type :replace-selected
                                                          :node replacement})))
                close-menu
                (dissoc :editing))
            (dissoc shell-state :editing)))))
    shell-state))

(defn- select-path [domain-state path]
  (editor/apply-command domain-state {:type :select
                                      :path path}))

(defn- select-shell-path [shell-state path]
  (assoc shell-state :domain (select-path (:domain shell-state) path)))

(defn- expandable-node-path? [shell-state path]
  (seq (editor/node-children (editor/node-at-path (get-in shell-state [:domain :root]) path))))

(defn expand-stack-node
  "Expands a stack child into the new breadcrumb endpoint when it has children."
  [shell-state path]
  (if (expandable-node-path? shell-state path)
    (-> (select-shell-path shell-state path)
        (assoc :expanded-path path)
        (assoc :stack-open? true)
        close-menu)
    shell-state))

(defn toggle-breadcrumb-expansion
  "Selects a breadcrumb node and toggles whether its child stack is visible."
  [shell-state path]
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

(defn toggle-menu-for-path
  "Opens or closes the action menu for a visible node path."
  [shell-state path]
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

(defn apply-domain-command
  "Applies an editor-domain command and closes any open menu."
  [shell-state command]
  (-> shell-state
      (assoc :domain (editor/apply-command (:domain shell-state) command))
      close-menu))

(defn activate-menu-action
  "Runs the command attached to a menu item when that item exists."
  [shell-state action-id]
  (if-let [{:keys [command transition]} (first (filter #(= action-id (:id %))
                                                       (available-menu-actions shell-state)))]
    (case transition
      :begin-editing (begin-editing shell-state)
      (apply-domain-command shell-state command))
    shell-state))

(defn step-menu-selection
  "Moves the highlighted menu item by offset while clamping to the menu bounds."
  [shell-state offset]
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

(defn move-selection
  "Moves either node selection or menu selection, depending on whether the menu is open."
  [shell-state direction]
  (if (:editing shell-state)
    shell-state
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
        shell-state))))
