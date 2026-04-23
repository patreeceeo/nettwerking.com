(ns app.frontend.app-test
  (:require [app.frontend.app :as app]
            [clojure.string :as string]
            [cljs.test :refer [deftest is use-fixtures]]))

(def ^:dynamic *container* nil)
(def ^:dynamic *storage-key* nil)

(defn- mount-app! []
  (app/mount-shell! *container*
                    {:storage js/localStorage
                     :storage-key *storage-key*
                     :persist-delay-ms 0}))

(defn- query-one [selector]
  (.querySelector *container* selector))

(defn- testid [value]
  (query-one (str "[data-testid='" value "']")))

(defn- text-content [selector]
  (some-> (query-one selector) .-textContent))

(defn- click! [element]
  (.dispatchEvent element
                  (js/MouseEvent. "click" #js {:bubbles true
                                               :cancelable true})))

(defn- input-text! [element text]
  (set! (.-textContent element) text)
  (.dispatchEvent element
                  (js/Event. "input" #js {:bubbles true
                                          :cancelable true})))

(defn- keydown! [element key]
  (.dispatchEvent element
                  (js/KeyboardEvent. "keydown" #js {:key key
                                                    :bubbles true
                                                    :cancelable true})))

(defn- place-caret-at-end! [element]
  (.focus element)
  (let [selection (.getSelection js/window)
        range (.createRange js/document)]
    (.selectNodeContents range element)
    (.collapse range false)
    (.removeAllRanges selection)
    (.addRange selection range)))

(defn- type-key! [element key]
  (keydown! element key)
  (when-not (= "Enter" key)
    (let [selection (.getSelection js/window)]
      (when-not (pos? (.-rangeCount selection))
        (throw (js/Error. "Missing text selection while typing into contenteditable.")))
      (let [range (.getRangeAt selection 0)
            text-node (.createTextNode js/document key)]
        (.deleteContents range)
        (.insertNode range text-node)
        (.setStartAfter range text-node)
        (.collapse range true)
        (.removeAllRanges selection)
        (.addRange selection range)
        (.dispatchEvent element
                        (js/Event. "input" #js {:bubbles true
                                                :cancelable true}))))))

(defn- keydown-document! [key]
  (.dispatchEvent js/document
                  (js/KeyboardEvent. "keydown" #js {:key key
                                                    :bubbles true
                                                    :cancelable true})))

(defn- selected-node-id []
  (some-> (query-one "[data-selected='true'][data-node-body='true']")
          (.getAttribute "data-testid")))

(defn- selected-action-id []
  (some-> (query-one "[data-action-selected='true'][data-menu-action='true']")
          (.getAttribute "data-testid")))

(defn- with-test-dom [test-fn]
  (let [container (.createElement js/document "div")
        storage-key (str app/default-storage-key "-test-" (.now js/Date))]
    (.appendChild (.-body js/document) container)
    (.removeItem js/localStorage storage-key)
    (binding [*container* container
              *storage-key* storage-key]
      (try
        (test-fn)
        (finally
          (app/unmount-shell!)
          (.removeItem js/localStorage storage-key)
          (.remove container))))))

(use-fixtures :each with-test-dom)

(deftest renders-the-first-run-shell
  (mount-app!)
  (is (= "Live Core Editor" (text-content "[data-testid='app-title']")))
  (is (= "First Run" (text-content "[data-testid='status-kind']")))
  (is (= "Make (+ 2 3). Finish the starter expression by adding one more number."
         (text-content "[data-testid='starter-task']")))
  (is (= "stack-node-args-1" (selected-node-id)))
  (is (= [] (:expanded-path (app/current-shell-state))))
  (is (some? (testid "breadcrumb-root")))
  (is (nil? (testid "action-menu")))
  (is (some? (testid "child-stack")))
  (click! (testid "breadcrumb-root"))
  (is (= "breadcrumb-root" (selected-node-id)))
  (is (= [] (:expanded-path (app/current-shell-state))))
  (is (false? (:stack-open? (app/current-shell-state))))
  (is (nil? (testid "child-stack")))
  (keydown! (testid "breadcrumb-root") " ")
  (is (true? (:stack-open? (app/current-shell-state))))
  (is (some? (testid "child-stack")))
  (keydown! (testid "breadcrumb-root") "ArrowDown")
  (is (= "stack-node-args-0" (selected-node-id)))
  (click! (testid "stack-node-args-0"))
  (is (= "stack-node-args-0" (selected-node-id)))
  (is (= [] (:expanded-path (app/current-shell-state))))
  (keydown! (testid "stack-node-args-0") " ")
  (is (= [] (:expanded-path (app/current-shell-state)))))

(deftest fills-the-starter-hole-to-visible-success
  (mount-app!)
  (is (= :call (get-in (app/current-state) [:root :type])))
  (click! (testid "menu-toggle-args-1"))
  (click! (testid "action-edit"))
  (input-text! (testid "edit-input-args-1") "3")
  (click! *container*)
  (is (= "Success" (text-content "[data-testid='status-kind']")))
  (is (= "5" (text-content "[data-testid='result-value']")))
  (is (= "stack-node-args-1" (selected-node-id)))
  (is (= "Success" (text-content "[data-testid='status-live']"))))

(deftest wraps-deletes-and-recovers-through-the-ui
  (mount-app!)
  (keydown! (testid "stack-node-args-1") "ArrowUp")
  (is (= "stack-node-args-0" (selected-node-id)))
  (keydown! (testid "stack-node-args-0") ".")
  (click! (testid "action-wrap"))
  (is (= :call (get-in (app/current-state) [:root :args 0 :type])))
  (is (= [:args 0] (:expanded-path (app/current-shell-state))))
  (is (= "stack-node-args-0-args-1" (selected-node-id)))
  (click! (testid "breadcrumb-root"))
  (is (= [] (:expanded-path (app/current-shell-state))))
  (is (= "breadcrumb-root" (selected-node-id)))
  (keydown! (testid "breadcrumb-root") "ArrowDown")
  (is (= "stack-node-args-0" (selected-node-id)))
  (click! (testid "stack-node-args-0"))
  (is (= [:args 0] (:expanded-path (app/current-shell-state))))
  (is (= "breadcrumb-args-0" (selected-node-id)))
  (click! (testid "breadcrumb-root"))
  (is (= [] (:expanded-path (app/current-shell-state))))
  (keydown! (testid "breadcrumb-root") "ArrowDown")
  (is (= "stack-node-args-0" (selected-node-id)))
  (keydown! (testid "stack-node-args-0") " ")
  (is (= [:args 0] (:expanded-path (app/current-shell-state))))
  (is (= "breadcrumb-args-0" (selected-node-id)))
  (click! (testid "menu-toggle-args-0-args-1"))
  (click! (testid "action-edit"))
  (input-text! (testid "edit-input-args-0-args-1") "4")
  (keydown! (testid "edit-input-args-0-args-1") "Enter")
  (is (= "Partial" (text-content "[data-testid='status-kind']")))
  (keydown! (testid "stack-node-args-0-args-1") "ArrowUp")
  (is (= "stack-node-args-0-args-0" (selected-node-id)))
  (keydown! (testid "stack-node-args-0-args-0") "ArrowUp")
  (is (= "breadcrumb-args-0" (selected-node-id)))
  (keydown! (testid "breadcrumb-args-0") " ")
  (is (= [:args 0] (:expanded-path (app/current-shell-state))))
  (is (false? (:stack-open? (app/current-shell-state))))
  (is (= "breadcrumb-args-0" (selected-node-id)))
  (keydown! (testid "breadcrumb-args-0") " ")
  (is (true? (:stack-open? (app/current-shell-state))))
  (keydown! (testid "breadcrumb-args-0") "ArrowUp")
  (is (= "breadcrumb-root" (selected-node-id)))
  (keydown! (testid "breadcrumb-root") " ")
  (is (= [] (:expanded-path (app/current-shell-state))))
  (is (true? (:stack-open? (app/current-shell-state))))
  (keydown! (testid "breadcrumb-root") "ArrowDown")
  (keydown! (testid "stack-node-args-0") "ArrowDown")
  (is (= "stack-node-args-1" (selected-node-id)))
  (click! (testid "menu-toggle-args-1"))
  (click! (testid "action-edit"))
  (input-text! (testid "edit-input-args-1") "3")
  (keydown! (testid "edit-input-args-1") "Enter")
  (is (= "Success" (text-content "[data-testid='status-kind']")))
  (is (= "11" (text-content "[data-testid='result-value']")))
  (keydown! (testid "stack-node-args-1") "ArrowUp")
  (is (= "stack-node-args-0" (selected-node-id)))
  (click! (testid "menu-toggle-args-0"))
  (click! (testid "action-delete"))
  (is (= "Partial" (text-content "[data-testid='status-kind']")))
  (click! (testid "menu-toggle-args-0"))
  (click! (testid "action-edit"))
  (input-text! (testid "edit-input-args-0") "4")
  (keydown! (testid "edit-input-args-0") "Enter")
  (is (= "Success" (text-content "[data-testid='status-kind']")))
  (is (= "7" (text-content "[data-testid='result-value']"))))

(deftest restores-a-saved-session-on-remount
  (mount-app!)
  (keydown! (testid "stack-node-args-1") "e")
  (input-text! (testid "edit-input-args-1") "3")
  (keydown! (testid "edit-input-args-1") "Enter")
  (app/unmount-shell!)
  (mount-app!)
  (is (= "Restored" (text-content "[data-testid='status-kind']")))
  (is (= "5" (text-content "[data-testid='result-value']")))
  (is (= "stack-node-args-1" (selected-node-id)))
  (is (= [] (:expanded-path (app/current-shell-state)))))

(deftest keyboard-navigation-and-menu-focus-work
  (mount-app!)
  (keydown! (testid "stack-node-args-1") "ArrowUp")
  (is (= "stack-node-args-0" (selected-node-id)))
  (keydown! (testid "stack-node-args-0") "ArrowUp")
  (is (= "breadcrumb-root" (selected-node-id)))
  (keydown! (testid "breadcrumb-root") "ArrowDown")
  (is (= "stack-node-args-0" (selected-node-id)))
  (keydown! (testid "stack-node-args-0") "ArrowRight")
  (is (= "stack-node-args-0" (selected-node-id)))
  (keydown! (testid "stack-node-args-0") ".")
  (is (= "action-edit" (selected-action-id)))
  (keydown! (testid "stack-node-args-1") "ArrowDown")
  (is (= "stack-node-args-0" (selected-node-id)))
  (is (= "action-wrap" (selected-action-id)))
  (keydown! (testid "stack-node-args-0") "ArrowDown")
  (is (= "action-delete" (selected-action-id)))
  (keydown! (testid "stack-node-args-0") "Enter")
  (is (= "Partial" (text-content "[data-testid='status-kind']")))
  (keydown! (testid "stack-node-args-0") ".")
  (is (= "action-edit" (selected-action-id)))
  (keydown! (testid "stack-node-args-0") "Enter")
  (input-text! (testid "edit-input-args-0") "4")
  (keydown! (testid "edit-input-args-0") "Enter")
  (is (= "Partial" (text-content "[data-testid='status-kind']")))
  (keydown! (testid "stack-node-args-0") "ArrowDown")
  (is (= "stack-node-args-1" (selected-node-id)))
  (keydown! (testid "stack-node-args-1") "e")
  (input-text! (testid "edit-input-args-1") "3")
  (keydown! (testid "edit-input-args-1") "Enter")
  (is (= "Success" (text-content "[data-testid='status-kind']")))
  (is (= "7" (text-content "[data-testid='result-value']"))))

(deftest opening-a-breadcrumb-menu-on-an-ancestor-re-expands-that-node
  (mount-app!)
  (keydown! (testid "stack-node-args-1") "ArrowUp")
  (is (= "stack-node-args-0" (selected-node-id)))
  (keydown! (testid "stack-node-args-0") ".")
  (click! (testid "action-wrap"))
  (is (= [:args 0] (:expanded-path (app/current-shell-state))))
  (is (= "stack-node-args-0-args-1" (selected-node-id)))
  (click! (testid "menu-toggle-root"))
  (is (= [] (:expanded-path (app/current-shell-state))))
  (is (= "breadcrumb-root" (selected-node-id)))
  (is (true? (get-in (app/current-shell-state) [:menu :open?])))
  (is (some? (testid "action-menu")))
  (is (some? (testid "child-stack"))))

(deftest keyboard-navigation-survives-clicking-dead-space
  (mount-app!)
  (click! *container*)
  (keydown-document! "ArrowUp")
  (is (= "stack-node-args-0" (selected-node-id)))
  (keydown-document! ".")
  (is (= "action-edit" (selected-action-id))))

(deftest editing-a-node-with-key-sequence-updates-its-value
  (mount-app!)
  (keydown! (testid "stack-node-args-1") "e")
  (let [editor-input (testid "edit-input-args-1")]
    (is (some? editor-input))
    (place-caret-at-end! editor-input)
    (doseq [key ["A" "B" "C" "Enter"]]
      (type-key! (testid "edit-input-args-1") key)))
  (is (string/includes? (or (text-content "[data-testid='stack-node-args-1']") "")
                        "ABC"))
  (is (= :symbol (get-in (app/current-state) [:root :args 1 :type])))
  (is (= "ABC" (get-in (app/current-state) [:root :args 1 :name]))))
