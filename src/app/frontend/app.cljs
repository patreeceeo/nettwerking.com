(ns app.frontend.app
  (:require [app.core.editor :as editor]
            [app.frontend.shell-state :as shell]
            [cljs.tools.reader.edn :as edn]
            [reagent.core :as r]
            [reagent.dom :as rdom]
            [clojure.string :as string]))

(def default-storage-key
  "The localStorage key used for persisted editor sessions."
  "live-core-editor-state")

(def default-persist-delay-ms
  "The default debounce delay for writing shell snapshots to storage."
  75)

;; Holds the mounted frontend instance so tests and reloads can inspect or tear it down.
(defonce current-instance (atom nil))

(defn- path-id [path]
  (if (empty? path)
    "root"
    (string/join "-" (map #(if (keyword? %) (name %) (str %)) path))))

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
  ;; The rendered tree is intentionally Lisp-like even though call nodes keep a
  ;; separate :fn field in the underlying AST.
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

(defn- update-shell! [instance update-fn persist?]
  (let [before @(:state instance)
        after (shell/normalize-shell-state (update-fn before))
        changed? (not= before after)]
    (when changed?
      ;; Every UI transition flows through shell normalization so selection,
      ;; expansion, and menu state stay in sync before the next render.
      (reset! (:state instance) after)
      (r/flush)
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

(defn- stop-event! [event]
  (.stopPropagation event))

(defn- handle-document-click! [instance event]
  (when (and (get-in @(:state instance) [:menu :open?])
             (not (.closest (.-target event) "[data-menu-context='true']")))
    (close-menu! instance)))

(defn- handle-document-keydown! [instance event]
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

      (and (= "Enter" (.-key event))
           (get-in @(:state instance) [:menu :open?]))
      (when-let [action-id (:id (shell/current-menu-action @(:state instance)))]
        (.preventDefault event)
        (activate-menu-action! instance action-id))

      :else nil)))

(defn- node-token-view [node]
  (let [display-node (displayed-node node)]
    [:<>
     [:span.node-token (node-text display-node)]
     [:span.node-meta (node-kind-label node)]]))

(defn- node-button-view [instance path node selected? extra-class testid region]
  (let [classes (cond-> ["node-button" extra-class]
                  selected? (conj "selected-node"))
        aria-label (str (string/capitalize (node-kind-label node))
                        ": "
                        (node-text node))
        on-click (fn [event]
                   (stop-event! event)
                   (case region
                     "breadcrumb" (toggle-breadcrumb-expansion! instance path)
                     "stack" (expand-stack-node! instance path)
                     nil))]
    [:button {:type "button"
              :class (string/join " " classes)
              :data-node-body "true"
              :data-node-id (path-id path)
              :data-node-region region
              :data-testid testid
              :data-selected (if selected? "true" "false")
              :aria-label aria-label
              :on-click on-click}
     [node-token-view node]]))

(defn- menu-toggle-view [instance path selected?]
  [:button {:type "button"
            :class (str "menu-toggle" (when selected? " selected-toggle"))
            :data-menu-toggle "true"
            :data-testid (str "menu-toggle-" (path-id path))
            :aria-label "Open actions"
            :on-click (fn [event]
                        (stop-event! event)
                        (toggle-menu-for-path! instance path))}
   "⋯"])

(defn- menu-items-view [instance shell-state]
  (let [selected-action-id (:id (shell/current-menu-action shell-state))]
    (into [:<>]
          (map (fn [{:keys [id label summary testid]}]
                 ^{:key (name id)}
                 [:button {:type "button"
                           :class (str "menu-action" (when (= id selected-action-id) " selected-action"))
                           :data-menu-action "true"
                           :data-action-id (name id)
                           :data-testid testid
                           :data-action-selected (if (= id selected-action-id) "true" "false")
                           :on-click (fn [event]
                                       (stop-event! event)
                                       (activate-menu-action! instance id))}
                  [:span.menu-action-copy
                   [:strong label]
                   [:span summary]]])
               (shell/available-menu-actions shell-state)))))

(defn- action-menu-view [instance shell-state]
  [:div.action-menu {:data-testid "action-menu"
                     :data-menu-context "true"
                     :on-click stop-event!}
   [:div.panel-heading "Actions"]
   [menu-items-view instance shell-state]])

(defn- breadcrumb-item-view [instance shell-state path]
  (let [domain-state (:domain shell-state)
        expanded-path (:expanded-path shell-state)
        selection (:selection domain-state)
        selected? (= selection path)
        expanded? (= expanded-path path)
        crumb-classes (cond-> ["breadcrumb-item"]
                        selected? (conj "selected-breadcrumb")
                        expanded? (conj "expanded-breadcrumb"))
        menu-open? (get-in shell-state [:menu :open?])
        selected-breadcrumb? (= :breadcrumb (shell/selection-region shell-state))]
    [:div {:class (string/join " " crumb-classes)
           :data-menu-context (when (and menu-open? selected? selected-breadcrumb?) "true")}
     [node-button-view instance
      path
      (editor/node-at-path (:root domain-state) path)
      selected?
      "breadcrumb-button"
      (str "breadcrumb-" (path-id path))
      "breadcrumb"]
     [menu-toggle-view instance path selected?]]))

(defn- breadcrumbs-view [instance shell-state]
  (let [expanded-path (:expanded-path shell-state)
        selected-breadcrumb? (= :breadcrumb (shell/selection-region shell-state))
        menu-open? (get-in shell-state [:menu :open?])]
    [:<>
     (into [:div.breadcrumbs {:data-testid "breadcrumbs"}]
           (map-indexed
            (fn [index path]
              ^{:key (str "crumb-fragment-" (path-id path))}
              [:<>
               (when (pos? index)
                 ^{:key (str "separator-" (path-id path))}
                 [:span.breadcrumb-separator "→"])
               [breadcrumb-item-view instance shell-state path]])
            (shell/breadcrumb-paths expanded-path)))
     (when (and selected-breadcrumb? menu-open?)
       [:div.breadcrumb-focus {:data-menu-context "true"
                               :data-testid "breadcrumb-focus"
                               :on-click stop-event!}
        [action-menu-view instance shell-state]])]))

(defn- stack-row-content-view [instance path node selected-row?]
  [:div {:class (str "stack-row" (when selected-row? " focus-row"))
         :data-testid (when selected-row? "focus-row")}
   [node-button-view instance
    path
    node
    selected-row?
    "stack-button"
    (str "stack-node-" (path-id path))
    "stack"]
   [menu-toggle-view instance path selected-row?]])

(defn- stack-row-view [instance shell-state path]
  (let [domain-state (:domain shell-state)
        node (editor/node-at-path (:root domain-state) path)
        selected? (= path (:selection domain-state))
        selected-stack? (= :stack (shell/selection-region shell-state))
        menu-open? (and selected? selected-stack? (get-in shell-state [:menu :open?]))
        selected-row? (and selected? selected-stack?)]
    (if menu-open?
      [:div.stack-row-shell {:data-menu-context "true"
                             :on-click stop-event!}
       [stack-row-content-view instance path node selected-row?]
       [action-menu-view instance shell-state]]
      [stack-row-content-view instance path node selected-row?])))

(defn- stack-view [instance shell-state]
  (let [domain-state (:domain shell-state)
        expanded-path (:expanded-path shell-state)
        stack-paths (shell/stack-child-paths (:root domain-state) expanded-path)]
    (when (:stack-open? shell-state)
      (if (seq stack-paths)
        (into [:div.child-stack {:data-testid "child-stack"}]
              (map (fn [path]
                     ^{:key (path-id path)}
                     [stack-row-view instance shell-state path])
                   stack-paths))
        [:div.empty-stack {:data-testid "empty-stack"}
         "This expanded node has no visible children yet."]))))

(defn- status-panel-view [shell-state]
  (let [domain-state (:domain shell-state)
        {status-title :title
         status-summary :summary} (status-copy domain-state)]
    [:aside.status-panel
     [:div.panel-heading "Result"]
     [:div.status-kind {:data-testid "status-kind"} status-title]
     (when status-summary
       [:div.status-summary {:data-testid "status-summary"} status-summary])
     [:div.result-value {:data-testid "result-value"} (result-copy domain-state)]
     [:div.status-live {:data-testid "status-live"
                        :aria-live "polite"}
      (if status-summary
        (str status-title ". " status-summary)
        status-title)]]))

(defn- app-root [instance]
  (let [shell-state @(:state instance)
        domain-state (:domain shell-state)
        {task-title :title
         task-description :description} (:task domain-state)]
    [:main.editor-shell
     [:header.orientation-header
      [:h1 {:data-testid "app-title"} "Live Core Editor"]
      [:p {:data-testid "starter-task"}
       [:strong task-title]
       " "
       task-description]]
     [:div.workspace-grid
      [:section.tree-panel {:aria-label "Focused tree editor"}
       [:div.panel-heading "Focused Tree Editor"]
       [breadcrumbs-view instance shell-state]
       [stack-view instance shell-state]]
      [status-panel-view shell-state]]]))

(defn render!
  "Renders the frontend shell into the instance container."
  [instance]
  (rdom/render [app-root instance] (:container instance)))

(defn current-state
  "Returns the current editor domain state for debugging and browser tests."
  []
  (some-> @current-instance :state deref :domain))

(defn current-shell-state
  "Returns the full UI shell state for debugging and browser tests."
  []
  (some-> @current-instance :state deref))

(defn- attach-events! [instance]
  (let [handle-document-click (fn [event]
                                (handle-document-click! instance event))
        handle-document-keydown (fn [event]
                                  (handle-document-keydown! instance event))]
    (.addEventListener js/document "click" handle-document-click)
    (.addEventListener js/document "keydown" handle-document-keydown)
    (assoc instance
           :handle-document-click handle-document-click
           :handle-document-keydown handle-document-keydown)))

(defn unmount-shell!
  "Unmounts the frontend shell and removes any document-level listeners."
  []
  (when-let [{:keys [container handle-document-click handle-document-keydown timeout-id]} @current-instance]
    (when-let [timeout @timeout-id]
      (js/clearTimeout timeout)
      (reset! timeout-id nil))
    (when handle-document-click
      (.removeEventListener js/document "click" handle-document-click))
    (when handle-document-keydown
      (.removeEventListener js/document "keydown" handle-document-keydown))
    (rdom/unmount-component-at-node container)
    (reset! current-instance nil)))

(defn mount-shell!
  "Mounts the frontend shell into container with optional storage overrides."
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
         ;; The shell persists UI-only expansion state separately from the
         ;; editor domain so restore needs to rehydrate both layers.
         shell-state (assoc (shell/initial-shell-state domain-state expanded-path)
                            :stack-open? (if (and (map? snapshot)
                                                  (contains? snapshot :stack-open?))
                                           (:stack-open? snapshot)
                                           true))
         instance (attach-events! {:container container
                                   :state (r/atom shell-state)
                                   :storage storage
                                   :storage-key storage-key
                                   :persist-delay-ms persist-delay-ms
                                   :timeout-id (atom nil)})]
     (render! instance)
     (reset! current-instance instance)
     instance)))

(defn init
  "Bootstraps the frontend app when the page container exists."
  []
  (when-let [container (.getElementById js/document "app")]
    (mount-shell! container)))

(defn ^:dev/after-load reload
  "Hot-reload entry point for the frontend shell."
  []
  (init))
