(ns app.frontend.app
  (:require [app.core.editor :as editor]
            [clojure.string :as string]
            [cljs.tools.reader.edn :as edn]
            [goog.string :as gstring]))

(def default-storage-key "live-core-editor-state")
(def default-persist-delay-ms 75)

(defonce current-instance (atom nil))

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

(defn- html-escape [value]
  (gstring/htmlEscape (str value)))

(defn- path-id [path]
  (if (empty? path)
    "root"
    (string/join "-" (map #(if (keyword? %) (name %) (str %)) path))))

(defn- path-data [path]
  (html-escape (pr-str path)))

(defn- parse-path [path-string]
  (edn/read-string path-string))

(defn- node-kind-label [node]
  (if (= :call (:type node))
    "form"
    (case (:type node)
      :literal "literal"
      :symbol "symbol"
      :hole "hole"
      "node")))

(declare node-text)

(defn- displayed-node [node]
  (if (= :call (:type node))
    (if-let [fn-symbol (editor/call-fn-symbol node)]
      (editor/symbol-node (name fn-symbol))
      {:type :hole :label "unknown form"})
    node))

(defn- node-text [node]
  (let [node (displayed-node node)]
    (case (:type node)
      :literal (str (:value node))
      :symbol (:name node)
      :hole (:label node)
      "unknown")))

(defn- reason-copy [reason]
  (case reason
    :invalid-selection "Pick a valid node first."
    :hole-selected "Fill this hole before wrapping it."
    :already-hole "This position is already empty."
    :no-parent "You are already at the top of this tree."
    :no-children "This node has no children to move into."
    :no-left-sibling "There is nothing to the left."
    :no-right-sibling "There is nothing to the right."
    :invalid-move "That move is not available here."
    :cannot-wrap-hole "Choose a real node before wrapping it."
    :invalid-saved-state "Saved work could not be restored."
    :invalid-saved-selection "Saved selection was no longer valid."
    "This action is not available here."))

(defn- status-copy [domain-state]
  (let [status (:status domain-state)
        evaluation (:eval domain-state)]
    (case (:kind status)
      :first-run
      {:title "First Run"
       :summary "Build expressions by shaping the tree."}

      :restored
      {:title "Restored"
       :summary "Restored from saved state."}

      :partial
      {:title "Partial"
       :summary "This expression is incomplete."}

      :success
      {:title "Success"
       :summary nil}

      :error
      {:title "Error"
       :summary (or (:message evaluation)
                    (reason-copy (:reason status)))}

      {:title "Editor"
       :summary nil})))

(defn- result-copy [domain-state]
  (let [evaluation (:eval domain-state)]
    (case (:kind evaluation)
      :success (str (:value evaluation))
      :partial "Waiting for the missing parts of the tree."
      :error (or (:message evaluation) "This expression could not be evaluated.")
      "No result yet.")))

(defn- parent-paths-inclusive [path]
  (loop [current path
         paths ()]
    (if (nil? current)
      (vec paths)
      (recur (editor/parent-path current)
             (conj paths current)))))

(defn- breadcrumb-paths [expanded-path]
  (parent-paths-inclusive expanded-path))

(defn- stack-child-paths [root expanded-path]
  (mapv (fn [index] (conj expanded-path :args index))
        (range (count (editor/node-args (editor/node-at-path root expanded-path))))))

(defn- default-expanded-path [domain-state]
  (let [selection (:selection domain-state)]
    (cond
      (not (editor/valid-node-path? (:root domain-state) selection)) []
      (empty? selection) []
      :else (or (editor/parent-path selection) selection))))

(defn- breadcrumb-selection? [expanded-path selection]
  (some #(= selection %) (breadcrumb-paths expanded-path)))

(defn- stack-selection? [expanded-path selection]
  (= expanded-path (editor/parent-path selection)))

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

(defn- available-menu-actions [shell-state]
  (->> (get-in shell-state [:domain :available-actions :actions])
       (filter :enabled?)
       (mapcat #(get menu-items-by-action-id (:id %)))
       vec))

(defn- normalize-menu [shell-state]
  (let [actions (available-menu-actions shell-state)
        action-count (count actions)
        open? (and (get-in shell-state [:menu :open?])
                   (pos? action-count))
        max-index (max 0 (dec action-count))
        action-index (min (get-in shell-state [:menu :action-index] 0) max-index)]
    (assoc shell-state :menu {:open? open?
                              :action-index action-index})))

(defn- normalize-shell-state [shell-state]
  (let [domain-state (:domain shell-state)
        expanded-path (normalize-expanded-path domain-state (:expanded-path shell-state))
        stack-open? (if (contains? shell-state :stack-open?)
                      (:stack-open? shell-state)
                      true)]
    (-> shell-state
        (assoc :expanded-path expanded-path)
        (assoc :stack-open? stack-open?)
        normalize-menu)))

(defn- initial-shell-state
  ([domain-state]
   (initial-shell-state domain-state nil))
  ([domain-state expanded-path]
   (normalize-shell-state {:domain domain-state
                           :expanded-path (or expanded-path (default-expanded-path domain-state))
                           :stack-open? true
                           :menu {:open? false
                                  :action-index 0}})))

(defn- selection-region [shell-state]
  (let [selection (get-in shell-state [:domain :selection])
        expanded-path (:expanded-path shell-state)]
    (cond
      (breadcrumb-selection? expanded-path selection) :breadcrumb
      (stack-selection? expanded-path selection) :stack
      :else :hidden)))

(defn- node-meta-label [node]
  (if (= :call (:type node))
    "form"
    (case (:type node)
      :literal "literal"
      :symbol "symbol"
      :hole "hole"
      "node")))

(defn- node-token-html [node]
  (let [display-node (displayed-node node)]
    (str "<span class='node-token'>" (html-escape (node-text display-node)) "</span>"
         "<span class='node-meta'>" (html-escape (node-meta-label node)) "</span>")))

(defn- current-menu-action [shell-state]
  (nth (available-menu-actions shell-state)
       (get-in shell-state [:menu :action-index] 0)
       nil))

(defn- render-node-button [path node selected? extra-class testid region]
  (let [classes (cond-> ["node-button" extra-class]
                  selected? (conj "selected-node"))
        aria-label (str (string/capitalize (node-kind-label node))
                        ": "
                        (node-text node))]
    (str "<button type='button'"
         " class='" (html-escape (string/join " " classes)) "'"
         " data-node-body='true'"
         " data-path='" (path-data path) "'"
         " data-node-id='" (html-escape (path-id path)) "'"
         " data-node-region='" (html-escape region) "'"
         " data-testid='" (html-escape testid) "'"
         " data-selected='" (if selected? "true" "false") "'"
         " tabindex='-1'"
         " aria-label='" (html-escape aria-label) "'>"
         (node-token-html node)
         "</button>")))

(defn- render-menu-toggle [path selected?]
  (str "<button type='button'"
       " class='menu-toggle" (when selected? " selected-toggle") "'"
       " data-menu-toggle='true'"
       " data-path='" (path-data path) "'"
       " data-testid='menu-toggle-" (html-escape (path-id path)) "'"
       " tabindex='-1'"
       " aria-label='Open actions'>⋯</button>"))

(defn- render-menu-items [shell-state]
  (let [selected-action-id (:id (current-menu-action shell-state))]
    (string/join
     ""
     (map (fn [{:keys [id label summary testid]}]
            (let [selected? (= id selected-action-id)]
              (str "<button type='button'"
                   " class='menu-action" (when selected? " selected-action") "'"
                   " data-menu-action='true'"
                   " data-action-id='" (html-escape (name id)) "'"
                   " data-testid='" (html-escape testid) "'"
                   " data-action-selected='" (if selected? "true" "false") "'"
                   " tabindex='-1'>"
                   "<span class='menu-action-copy'>"
                   "<strong>" (html-escape label) "</strong>"
                   "<span>" (html-escape summary) "</span>"
                   "</span>"
                   "</button>")))
          (available-menu-actions shell-state)))))

(defn- render-breadcrumbs [shell-state]
  (let [domain-state (:domain shell-state)
        expanded-path (:expanded-path shell-state)
        selection (:selection domain-state)
        menu-open? (get-in shell-state [:menu :open?])
        selected-breadcrumb? (= :breadcrumb (selection-region shell-state))]
    (str "<div class='breadcrumbs' data-testid='breadcrumbs'>"
         (string/join
          "<span class='breadcrumb-separator'>→</span>"
          (map (fn [path]
                 (let [node (editor/node-at-path (:root domain-state) path)
                       selected? (= selection path)
                       expanded? (= expanded-path path)
                       crumb-classes (cond-> ["breadcrumb-item"]
                                       selected? (conj "selected-breadcrumb")
                                       expanded? (conj "expanded-breadcrumb"))]
                   (str "<div class='" (html-escape (string/join " " crumb-classes)) "'"
                        (when (and menu-open? selected? selected-breadcrumb?)
                          " data-menu-context='true'")
                        ">"
                        (render-node-button path
                                            node
                                            selected?
                                            "breadcrumb-button"
                                            (str "breadcrumb-" (path-id path))
                                            "breadcrumb")
                        (render-menu-toggle path selected?)
                        "</div>")))
               (breadcrumb-paths expanded-path)))
         "</div>"
         (when (and selected-breadcrumb? menu-open?)
           (str "<div class='breadcrumb-focus'"
                " data-menu-context='true'"
                " data-testid='breadcrumb-focus'>"
                "<div class='action-menu' data-testid='action-menu'>"
                "<div class='panel-heading'>Actions</div>"
                (render-menu-items shell-state)
                "</div>"
                "</div>")))))

(defn- render-stack-row [shell-state path]
  (let [domain-state (:domain shell-state)
        node (editor/node-at-path (:root domain-state) path)
        selected? (= path (:selection domain-state))
        menu-open? (and selected? (= :stack (selection-region shell-state)) (get-in shell-state [:menu :open?]))
        selected-row? (and selected? (= :stack (selection-region shell-state)))]
    (if menu-open?
      (str "<div class='stack-row-shell' data-menu-context='true'>"
           "<div class='stack-row" (when selected-row? " focus-row") "'"
           (when selected-row? " data-testid='focus-row'")
           ">"
           (render-node-button path
                               node
                               selected-row?
                               "stack-button"
                               (str "stack-node-" (path-id path))
                               "stack")
           (render-menu-toggle path selected-row?)
           "</div>"
           "<div class='action-menu' data-testid='action-menu'>"
           "<div class='panel-heading'>Actions</div>"
           (render-menu-items shell-state)
           "</div>"
           "</div>")
      (str "<div class='stack-row" (when selected-row? " focus-row") "'"
           (when selected-row? " data-testid='focus-row'")
           ">"
           (render-node-button path
                               node
                               selected-row?
                               "stack-button"
                               (str "stack-node-" (path-id path))
                               "stack")
           (render-menu-toggle path selected-row?)
           "</div>"))))

(defn- render-stack [shell-state]
  (let [domain-state (:domain shell-state)
        expanded-path (:expanded-path shell-state)
        stack-paths (stack-child-paths (:root domain-state) expanded-path)]
    (when (:stack-open? shell-state)
      (if (seq stack-paths)
        (str "<div class='child-stack' data-testid='child-stack'>"
             (string/join "" (map #(render-stack-row shell-state %) stack-paths))
             "</div>")
        "<div class='empty-stack' data-testid='empty-stack'>This expanded node has no visible children yet.</div>"))))

(defn- app-html [shell-state]
  (let [domain-state (:domain shell-state)
        {task-title :title
         task-description :description} (:task domain-state)
        {status-title :title
         status-summary :summary} (status-copy domain-state)]
    (str "<main class='editor-shell'>"
         "<header class='orientation-header'>"
         "<h1 data-testid='app-title'>Live Core Editor</h1>"
         "<p data-testid='starter-task'><strong>" (html-escape task-title) "</strong> "
         (html-escape task-description) "</p>"
         "</header>"

         "<div class='workspace-grid'>"
         "<section class='tree-panel' aria-label='Focused tree editor'>"
         "<div class='panel-heading'>Focused Tree Editor</div>"
         (render-breadcrumbs shell-state)
         (render-stack shell-state)
         "</section>"

         "<aside class='status-panel'>"
         "<div class='panel-heading'>Result</div>"
         "<div class='status-kind' data-testid='status-kind'>" (html-escape status-title) "</div>"
         (when status-summary
           (str "<div class='status-summary' data-testid='status-summary'>" (html-escape status-summary) "</div>"))
         "<div class='result-value' data-testid='result-value'>" (html-escape (result-copy domain-state)) "</div>"
         "<div class='status-live' data-testid='status-live' aria-live='polite'>"
         (html-escape (if status-summary
                        (str status-title ". " status-summary)
                        status-title))
         "</div>"
         "</aside>"
         "</div>"
         "</main>")))

(defn- state->snapshot [shell-state]
  {:root (get-in shell-state [:domain :root])
   :selection (get-in shell-state [:domain :selection])
   :expanded-path (:expanded-path shell-state)
   :stack-open? (:stack-open? shell-state)})

(defn- storage-get [storage storage-key]
  (when storage
    (try
      (.getItem storage storage-key)
      (catch :default _error
        ::storage-unavailable))))

(defn- storage-set! [storage storage-key value]
  (when storage
    (try
      (.setItem storage storage-key value)
      true
      (catch :default _error
        false))))

(defn- load-snapshot [storage storage-key]
  (let [raw-value (storage-get storage storage-key)]
    (cond
      (or (nil? raw-value) (= ::storage-unavailable raw-value))
      nil

      :else
      (try
        (edn/read-string raw-value)
        (catch :default _error
          ::invalid-snapshot)))))

(defn- persist-now! [instance]
  (let [{:keys [storage storage-key state timeout-id]} instance]
    (reset! timeout-id nil)
    (storage-set! storage
                  storage-key
                  (pr-str (state->snapshot @state)))))

(defn- schedule-persist! [instance]
  (let [{:keys [persist-delay-ms timeout-id]} instance]
    (when-let [timeout @timeout-id]
      (js/clearTimeout timeout))
    (if (zero? persist-delay-ms)
      (persist-now! instance)
      (reset! timeout-id
              (js/setTimeout #(persist-now! instance) persist-delay-ms)))))

(declare render!)

(defn- update-shell! [instance update-fn persist?]
  (let [before @(:state instance)
        after (normalize-shell-state (update-fn before))
        changed? (not= before after)]
    (when changed?
      (reset! (:state instance) after)
      (render! instance)
      (when persist?
        (schedule-persist! instance)))))

(defn- close-menu! [instance]
  (update-shell! instance #(assoc % :menu {:open? false :action-index 0}) false))

(defn- select-path [domain-state path]
  (editor/apply-command domain-state {:type :select
                                      :path path}))

(defn- expandable-node-path? [shell-state path]
  (seq (editor/node-args (editor/node-at-path (get-in shell-state [:domain :root]) path))))

(defn- expand-stack-node! [instance path]
  (update-shell! instance
                 (fn [shell-state]
                   (if (expandable-node-path? shell-state path)
                     (-> shell-state
                         (assoc :domain (select-path (:domain shell-state) path))
                         (assoc :expanded-path path)
                         (assoc :stack-open? true)
                         (assoc :menu {:open? false :action-index 0}))
                     shell-state))
                 true))

(defn- toggle-breadcrumb-expansion! [instance path]
  (update-shell! instance
                 (fn [shell-state]
                   (let [expanded-path (:expanded-path shell-state)
                         same-path? (= path expanded-path)
                         target-expanded-path (if same-path? expanded-path path)
                         target-selection path
                         target-stack-open? (if same-path?
                                              (not (:stack-open? shell-state))
                                              true)]
                     (-> shell-state
                         (assoc :domain (select-path (:domain shell-state) target-selection))
                         (assoc :expanded-path target-expanded-path)
                         (assoc :stack-open? target-stack-open?)
                         (assoc :menu {:open? false :action-index 0}))))
                 true))

(defn- toggle-menu-for-path! [instance path]
  (update-shell! instance
                 (fn [shell-state]
                   (let [same-selection? (= path (get-in shell-state [:domain :selection]))
                         same-expanded-path? (= path (:expanded-path shell-state))
                         breadcrumb-path? (breadcrumb-selection? (:expanded-path shell-state) path)
                         shell-state (if same-selection?
                                       shell-state
                                       (assoc shell-state :domain (select-path (:domain shell-state) path)))
                         open? (and same-selection? (get-in shell-state [:menu :open?]))
                         opening? (not open?)]
                     (cond-> shell-state
                       (and opening? breadcrumb-path? (not same-expanded-path?))
                       (assoc :expanded-path path
                              :stack-open? true)

                       true
                       (assoc :menu {:open? opening?
                                     :action-index 0}))))
                 false))

(defn- apply-domain-command! [instance command]
  (update-shell! instance
                 (fn [shell-state]
                   (-> shell-state
                       (assoc :domain (editor/apply-command (:domain shell-state) command))
                       (assoc :menu {:open? false :action-index 0})))
                 true))

(defn- menu-action-by-id [shell-state action-id]
  (first (filter #(= action-id (:id %))
                 (available-menu-actions shell-state))))

(defn- activate-menu-action! [instance action-id]
  (when-let [{:keys [command]} (menu-action-by-id @(:state instance) action-id)]
    (apply-domain-command! instance command)))

(defn- step-menu-selection [shell-state offset]
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

(defn- move-selection-target [shell-state direction]
  (case (selection-region shell-state)
    :stack (or (stack-move-target shell-state direction)
               nil)
    :breadcrumb (breadcrumb-move-target shell-state direction)
    nil))

(defn- movement-direction [key]
  (case key
    ("ArrowUp" "k" "K") :up
    ("ArrowDown" "j" "J") :down
    ("ArrowLeft" "h" "H") :left
    ("ArrowRight" "l" "L") :right
    nil))

(defn- handle-move! [instance direction]
  (let [shell-state @(:state instance)]
    (if (get-in shell-state [:menu :open?])
      (update-shell! instance
                     #(case direction
                        (:up :left) (step-menu-selection % -1)
                        (:down :right) (step-menu-selection % 1)
                        %)
                     false)
      (when-let [target (move-selection-target shell-state direction)]
        (update-shell! instance
                       (fn [state]
                         (assoc state
                                :domain (select-path (:domain state) target)
                                :menu {:open? false
                                       :action-index 0}))
                       true)))))

(defn render! [instance]
  (set! (.-innerHTML (:container instance))
        (app-html @(:state instance))))

(defn current-state []
  (some-> @current-instance :state deref :domain))

(defn current-shell-state []
  (some-> @current-instance :state deref))

(defn- attach-events! [instance]
  (let [container (:container instance)
        document js/document
        handle-click (fn [event]
                       (let [target (.-target event)
                             shell-state @(:state instance)
                             inside-menu-context? (.closest target "[data-menu-context='true']")
                             node-body (.closest target "[data-node-body='true']")
                             menu-toggle (.closest target "[data-menu-toggle='true']")
                             menu-action (.closest target "[data-menu-action='true']")]
                         (cond
                           (and (get-in shell-state [:menu :open?])
                                (not inside-menu-context?))
                           (close-menu! instance)

                           menu-action
                           (activate-menu-action! instance
                                                  (keyword (.getAttribute menu-action "data-action-id")))

                           menu-toggle
                           (toggle-menu-for-path! instance
                                                  (parse-path (.getAttribute menu-toggle "data-path")))

                           node-body
                           (let [path (parse-path (.getAttribute node-body "data-path"))
                                 region (.getAttribute node-body "data-node-region")]
                             (case region
                               "breadcrumb" (toggle-breadcrumb-expansion! instance path)
                               "stack" (expand-stack-node! instance path)
                               nil))

                           :else nil)))
        handle-keydown (fn [event]
                         (let [direction (movement-direction (.-key event))]
                           (cond
                             direction
                             (do
                               (.preventDefault event)
                               (handle-move! instance direction))

                             (= "." (.-key event))
                             (do
                               (.preventDefault event)
                               (toggle-menu-for-path! instance (get-in @(:state instance) [:domain :selection])))

                             (= " " (.-key event))
                             (when-not (get-in @(:state instance) [:menu :open?])
                               (.preventDefault event)
                               (case (selection-region @(:state instance))
                                 :breadcrumb
                                 (toggle-breadcrumb-expansion! instance
                                                               (get-in @(:state instance) [:domain :selection]))
                                 :stack
                                 (expand-stack-node! instance
                                                     (get-in @(:state instance) [:domain :selection]))
                                 nil))

                             (= "Enter" (.-key event))
                             (when-let [action-id (:id (current-menu-action @(:state instance)))]
                               (.preventDefault event)
                               (activate-menu-action! instance action-id))

                             :else nil)))]
    (.addEventListener container "click" handle-click)
    (.addEventListener document "keydown" handle-keydown)
    (assoc instance
           :handle-click handle-click
           :handle-keydown handle-keydown)))

(defn unmount-shell! []
  (when-let [{:keys [container handle-click handle-keydown timeout-id]} @current-instance]
    (when-let [timeout @timeout-id]
      (js/clearTimeout timeout)
      (reset! timeout-id nil))
    (when handle-click
      (.removeEventListener container "click" handle-click))
    (when handle-keydown
      (.removeEventListener js/document "keydown" handle-keydown))
    (set! (.-innerHTML container) "")
    (reset! current-instance nil)))

(defn mount-shell!
  ([container]
   (mount-shell! container {}))
  ([container {:keys [storage storage-key persist-delay-ms]
               :or {storage js/localStorage
                    storage-key default-storage-key
                    persist-delay-ms default-persist-delay-ms}}]
   (unmount-shell!)
   (let [snapshot (load-snapshot storage storage-key)
         domain-state (editor/restore-state snapshot)
         expanded-path (when (map? snapshot)
                         (:expanded-path snapshot))
         shell-state (assoc (initial-shell-state domain-state expanded-path)
                            :stack-open? (if (and (map? snapshot)
                                                  (contains? snapshot :stack-open?))
                                           (:stack-open? snapshot)
                                           true))
         instance (attach-events! {:container container
                                   :state (atom shell-state)
                                   :storage storage
                                   :storage-key storage-key
                                   :persist-delay-ms persist-delay-ms
                                   :timeout-id (atom nil)})]
     (render! instance)
     (reset! current-instance instance)
     instance)))

(defn init []
  (when-let [container (.getElementById js/document "app")]
    (mount-shell! container)))

(defn ^:dev/after-load reload []
  (init))
