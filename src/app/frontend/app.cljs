(ns app.frontend.app
  (:require [app.core.editor :as editor]
            [app.frontend.shell-state :as shell]
            [clojure.string :as string]
            [cljs.tools.reader.edn :as edn]
            [goog.string :as gstring]))

(def default-storage-key "live-core-editor-state")
(def default-persist-delay-ms 75)

(defonce current-instance (atom nil))

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
  (let [selected-action-id (:id (shell/current-menu-action shell-state))]
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
          (shell/available-menu-actions shell-state)))))

(defn- render-breadcrumbs [shell-state]
  (let [domain-state (:domain shell-state)
        expanded-path (:expanded-path shell-state)
        selection (:selection domain-state)
        menu-open? (get-in shell-state [:menu :open?])
        selected-breadcrumb? (= :breadcrumb (shell/selection-region shell-state))]
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
               (shell/breadcrumb-paths expanded-path)))
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
        selected-stack? (= :stack (shell/selection-region shell-state))
        menu-open? (and selected? selected-stack? (get-in shell-state [:menu :open?]))
        selected-row? (and selected? selected-stack?)]
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
        stack-paths (shell/stack-child-paths (:root domain-state) expanded-path)]
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
                  (pr-str (shell/state->snapshot @state)))))

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
        after (shell/normalize-shell-state (update-fn before))
        changed? (not= before after)]
    (when changed?
      (reset! (:state instance) after)
      (render! instance)
      (when persist?
        (schedule-persist! instance)))))

(defn- close-menu! [instance]
  (update-shell! instance shell/close-menu false))

(defn- expand-stack-node! [instance path]
  (update-shell! instance #(shell/expand-stack-node % path) true))

(defn- toggle-breadcrumb-expansion! [instance path]
  (update-shell! instance #(shell/toggle-breadcrumb-expansion % path) true))

(defn- toggle-menu-for-path! [instance path]
  (update-shell! instance #(shell/toggle-menu-for-path % path) false))

(defn- apply-domain-command! [instance command]
  (update-shell! instance #(shell/apply-domain-command % command) true))

(defn- activate-menu-action! [instance action-id]
  (update-shell! instance #(shell/activate-menu-action % action-id) true))

(defn- movement-direction [key]
  (case key
    ("ArrowUp" "k" "K") :up
    ("ArrowDown" "j" "J") :down
    ("ArrowLeft" "h" "H") :left
    ("ArrowRight" "l" "L") :right
    nil))

(defn- handle-move! [instance direction]
  (update-shell! instance #(shell/move-selection % direction) true))

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
                               (case (shell/selection-region @(:state instance))
                                 :breadcrumb
                                 (toggle-breadcrumb-expansion! instance
                                                               (get-in @(:state instance) [:domain :selection]))
                                 :stack
                                 (expand-stack-node! instance
                                                     (get-in @(:state instance) [:domain :selection]))
                                 nil))

                             (= "Enter" (.-key event))
                             (when-let [action-id (:id (shell/current-menu-action @(:state instance)))]
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
         shell-state (assoc (shell/initial-shell-state domain-state expanded-path)
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
