(ns app.frontend.app
  (:require [app.core.editor :as editor]
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

(defn- action-by-id [state action-id]
  (first (filter #(= action-id (:id %))
                 (get-in state [:available-actions :actions]))))

(defn- reason-copy [reason]
  (case reason
    :invalid-selection "Pick a valid node first."
    :hole-selected "Fill this hole before wrapping it."
    :already-hole "This position is already empty."
    :no-parent "You are already at the top of this tree."
    :no-children "This node has no children to move into."
    :no-left-sibling "There is nothing to the left."
    :no-right-sibling "There is nothing to the right."
    :invalid-move "That keyboard move is not available here."
    :cannot-wrap-hole "Choose a real node before wrapping it."
    :invalid-saved-state "Saved work could not be restored."
    :invalid-saved-selection "Saved selection was no longer valid."
    "This action is not available here."))

(defn- status-copy [state]
  (let [status (:status state)
        evaluation (:eval state)]
    (case (:kind status)
      :first-run
      {:title "First Run"
       :summary "Build expressions by shaping the tree."
       :next-step "Start with the highlighted placeholder."}

      :restored
      {:title "Restored"
       :summary "Your last editor state was restored."
       :next-step (case (:kind evaluation)
                    :success "Keep exploring from the restored selection."
                    :partial "Finish the remaining hole to complete the expression."
                    :error "Replace the broken part of the tree to recover."
                    "Continue editing.")}

      :partial
      {:title "Partial"
       :summary "This expression is still missing a required part."
       :next-step "Fill the highlighted hole or move to another incomplete node."}

      :success
      {:title "Success"
       :summary (str "Current value: " (:value evaluation))
       :next-step "Try wrapping a value or replacing a node to keep exploring."}

      :error
      {:title "Error"
       :summary (or (:message evaluation)
                    (reason-copy (:reason status)))
       :next-step "Replace the selected subtree with something valid."}

      {:title "Editor"
       :summary "Ready."
       :next-step "Select a node to continue."})))

(defn- selection-copy [state]
  (let [selected-node (editor/node-at-path (:root state) (:selection state))
        node-type (:type selected-node)]
    (cond
      (= :call node-type)
      "Selected form. Move into its arguments or replace the whole form."

      (= :hole node-type)
      "Selected hole. Add a value or choose a symbol next."

      (= :literal node-type)
      "Selected literal. You can wrap it, replace it, or move around the tree."

      (= :symbol node-type)
      "Selected symbol. You can replace it or move around the tree."

      :else
      "Select a node to continue.")))

(defn- result-copy [state]
  (let [evaluation (:eval state)]
    (case (:kind evaluation)
      :success (str (:value evaluation))
      :partial "Waiting for the missing parts of the tree."
      :error (or (:message evaluation) "This expression could not be evaluated.")
      "No result yet.")))

(declare render-node)

(defn- node-meta-label [node]
  (if (= :call (:type node))
    "form"
    (case (:type node)
      :literal "literal"
      :symbol "symbol"
      :hole "hole"
      "node")))

(defn- render-node-content [node]
  (let [display-node (displayed-node node)]
    (case (:type display-node)
      :literal
      (str "<span class='node-value'>" (html-escape (:value display-node)) "</span>"
           "<span class='node-meta'>" (node-meta-label node) "</span>")

      :symbol
      (str "<span class='node-value'>" (html-escape (:name display-node)) "</span>"
           "<span class='node-meta'>" (node-meta-label node) "</span>")

      :hole
      (str "<span class='node-value'>" (html-escape (:label display-node)) "</span>"
           "<span class='node-meta'>" (node-meta-label node) "</span>")

      "<span class='node-value'>unknown node</span>")))

(defn- render-node-button [state path node content]
  (let [selected? (= path (:selection state))
        classes (cond-> ["node-button" (str "node-" (name (:type (displayed-node node))))]
                  selected? (conj "selected-node"))
        aria-label (str (string/capitalize (node-kind-label node))
                        ": "
                        (node-text node))]
    (str "<button type='button'"
         " class='" (html-escape (string/join " " classes)) "'"
         " data-node-button='true'"
         " data-path='" (path-data path) "'"
         " data-node-id='" (html-escape (path-id path)) "'"
         " data-testid='node-" (html-escape (path-id path)) "'"
         " data-selected='" (if selected? "true" "false") "'"
         " tabindex='" (if selected? "0" "-1") "'"
         " aria-label='" (html-escape aria-label) "'>"
         content
         "</button>")))

(defn- render-children [state path args]
  (string/join
   ""
   (map-indexed (fn [index arg]
                  (str "<div class='tree-branch'>"
                       "<div class='branch-label'>arg " (inc index) "</div>"
                       (render-node state (conj path :args index) arg)
                       "</div>"))
                args)))

(defn- render-node [state path node]
  (let [button-html
        (render-node-button state
                            path
                            node
                            (render-node-content node))
        args (editor/node-args node)]
    (str "<div class='tree-form'>"
         button-html
         (when (seq args)
           (str "<div class='tree-children'>"
                (render-children state path args)
                "</div>"))
         "</div>")))

(defn- action-button [state action-id {:keys [label testid command attrs]}]
  (let [{:keys [enabled? reason]} (action-by-id state action-id)
        attribute-string (apply str
                                (map (fn [[key value]]
                                       (str " data-" (name key) "='" (html-escape value) "'"))
                                     attrs))]
    (str "<button type='button'"
         " class='action-button'"
         " data-command='" (html-escape command) "'"
         " data-testid='" (html-escape testid) "'"
         attribute-string
         (when-not enabled? " disabled='disabled'")
         (when reason
           (str " title='" (html-escape (reason-copy reason)) "'"))
         ">"
         (html-escape label)
         "</button>")))

(defn- app-html [state]
  (let [{task-title :title
         task-description :description} (:task state)
        {status-title :title
         status-summary :summary
         status-next-step :next-step} (status-copy state)]
    (str "<main class='editor-shell'>"
         "<header class='orientation-header'>"
         "<h1 data-testid='app-title'>Live Core Editor</h1>"
         "<p class='orientation-copy'>Build expressions by shaping the tree.</p>"
         "<p data-testid='starter-task'><strong>" (html-escape task-title) "</strong> "
         (html-escape task-description) "</p>"
         "</header>"

         "<div class='workspace-grid'>"
         "<section class='tree-panel' aria-label='Expression tree'>"
         "<div class='panel-heading'>Structural Editor</div>"
         "<div class='selection-copy' data-testid='selection-copy'>"
         (html-escape (selection-copy state))
         "</div>"
         "<div class='tree-root' data-testid='tree-root'>"
         (render-node state [] (:root state))
         "</div>"
         "</section>"

         "<aside class='status-panel'>"
         "<div class='panel-heading'>Result</div>"
         "<div class='status-kind' data-testid='status-kind'>" (html-escape status-title) "</div>"
         "<div class='status-summary' data-testid='status-summary'>" (html-escape status-summary) "</div>"
         "<div class='status-next-step' data-testid='status-next-step'>" (html-escape status-next-step) "</div>"
         "<div class='result-value' data-testid='result-value'>" (html-escape (result-copy state)) "</div>"
         "<div class='status-live' data-testid='status-live' aria-live='polite'>"
         (html-escape (str status-title ". " status-summary))
         "</div>"
         "</aside>"
         "</div>"

         "<nav class='action-rail' data-testid='action-rail' aria-label='Selection actions'>"
         "<div class='panel-heading'>Actions</div>"
         (action-button state :insert-literal {:label "Insert 3"
                                               :testid "action-insert-literal-3"
                                               :command "insert-literal"
                                               :attrs {:value "3"}})
         (action-button state :insert-literal {:label "Insert 4"
                                               :testid "action-insert-literal-4"
                                               :command "insert-literal"
                                               :attrs {:value "4"}})
         (action-button state :insert-symbol {:label "Use +"
                                              :testid "action-insert-symbol-plus"
                                              :command "insert-symbol"
                                              :attrs {:name "+"}})
         (action-button state :insert-symbol {:label "Use wat"
                                              :testid "action-insert-symbol-wat"
                                              :command "insert-symbol"
                                              :attrs {:name "wat"}})
         (action-button state :wrap-selected {:label "Wrap In *"
                                              :testid "action-wrap"
                                              :command "wrap-selected"
                                              :attrs {:operator-name "*"}})
         (action-button state :delete-selected {:label "Delete"
                                                :testid "action-delete"
                                                :command "delete-selected"
                                                :attrs {}})
         (action-button state :move-selection-parent {:label "Parent"
                                                      :testid "action-move-parent"
                                                      :command "move-selection"
                                                      :attrs {:direction "parent"}})
         (action-button state :move-selection-first-child {:label "First Child"
                                                           :testid "action-move-first-child"
                                                           :command "move-selection"
                                                           :attrs {:direction "first-child"}})
         (action-button state :move-selection-left-sibling {:label "Left"
                                                            :testid "action-move-left"
                                                            :command "move-selection"
                                                            :attrs {:direction "left-sibling"}})
         (action-button state :move-selection-right-sibling {:label "Right"
                                                             :testid "action-move-right"
                                                             :command "move-selection"
                                                             :attrs {:direction "right-sibling"}})
         "</nav>"
         "</main>")))

(defn- state->snapshot [state]
  {:root (:root state)
   :selection (:selection state)})

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

(defn- selected-node-element [instance]
  (let [selected-id (path-id (:selection @(:state instance)))]
    (.querySelector (:container instance)
                    (str "[data-node-id='" selected-id "'][data-node-button='true']"))))

(defn- focus-selected-node! [instance]
  (when-let [selected-element (selected-node-element instance)]
    (.focus selected-element)))

(declare render!)

(defn dispatch-command! [instance command]
  (swap! (:state instance) editor/apply-command command)
  (render! instance)
  (schedule-persist! instance))

(defn- parse-command [element]
  (let [command-type (.getAttribute element "data-command")]
    (case command-type
      "select" {:type :select
                :path (parse-path (.getAttribute element "data-path"))}
      "insert-literal" {:type :insert-literal
                        :value (edn/read-string (or (.getAttribute element "data-value") "0"))}
      "insert-symbol" {:type :insert-symbol
                       :name (.getAttribute element "data-name")}
      "wrap-selected" {:type :wrap-selected
                       :operator-name (.getAttribute element "data-operator-name")}
      "delete-selected" {:type :delete-selected}
      "move-selection" {:type :move-selection
                        :direction (keyword (.getAttribute element "data-direction"))}
      nil)))

(defn- node-command [element]
  (when-let [path-string (.getAttribute element "data-path")]
    {:type :select
     :path (parse-path path-string)}))

(defn render! [instance]
  (set! (.-innerHTML (:container instance))
        (app-html @(:state instance)))
  (focus-selected-node! instance))

(defn current-state []
  (some-> @current-instance :state deref))

(defn- keydown-command [event]
  (case (.-key event)
    "ArrowUp" {:type :move-selection :direction :parent}
    "ArrowDown" {:type :move-selection :direction :first-child}
    "ArrowLeft" {:type :move-selection :direction :left-sibling}
    "ArrowRight" {:type :move-selection :direction :right-sibling}
    nil))

(defn- attach-events! [instance]
  (let [container (:container instance)
        handle-click (fn [event]
                       (when-let [element (.closest (.-target event) "button")]
                         (cond
                           (.hasAttribute element "data-node-button")
                           (dispatch-command! instance (node-command element))

                           (.hasAttribute element "data-command")
                           (when-let [command (parse-command element)]
                             (dispatch-command! instance command))

                           :else nil)))
        handle-keydown (fn [event]
                         (when (and (.closest (.-target event) "[data-node-button='true']")
                                    (keydown-command event))
                           (.preventDefault event)
                           (dispatch-command! instance (keydown-command event))))]
    (.addEventListener container "click" handle-click)
    (.addEventListener container "keydown" handle-keydown)
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
      (.removeEventListener container "keydown" handle-keydown))
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
         initial-state (editor/restore-state snapshot)
         instance (attach-events! {:container container
                                   :state (atom initial-state)
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
