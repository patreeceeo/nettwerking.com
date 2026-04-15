(ns app.frontend.app-test
  (:require [app.frontend.app :as app]
            [clojure.string :as string]
            [cljs.test :refer [deftest is testing use-fixtures]]))

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

(defn- keydown! [element key]
  (.dispatchEvent element
                  (js/KeyboardEvent. "keydown" #js {:key key
                                                    :bubbles true
                                                    :cancelable true})))

(defn- selected-node-id []
  (some-> (query-one "[data-selected='true'][data-node-button='true']")
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
  (is (= "node-args-1" (selected-node-id)))
  (is (= "true" (.getAttribute (testid "node-args-1") "data-selected")))
  (is (some? (.getAttribute (testid "action-wrap") "disabled"))))

(deftest fills-the-starter-hole-to-visible-success
  (mount-app!)
  (click! (testid "action-insert-literal-3"))
  (is (= "Success" (text-content "[data-testid='status-kind']")))
  (is (= "5" (text-content "[data-testid='result-value']")))
  (is (= "node-args-1" (selected-node-id)))
  (is (string/includes? (text-content "[data-testid='status-live']")
                        "Success. Current value: 5")))

(deftest wraps-deletes-and-recovers-through-the-ui
  (mount-app!)
  (click! (testid "node-args-0"))
  (click! (testid "action-wrap"))
  (is (= "Partial" (text-content "[data-testid='status-kind']")))
  (is (= "node-args-0-args-1" (selected-node-id)))
  (click! (testid "action-insert-literal-4"))
  (is (= "Partial" (text-content "[data-testid='status-kind']")))
  (click! (testid "node-args-1"))
  (click! (testid "action-insert-literal-3"))
  (is (= "Success" (text-content "[data-testid='status-kind']")))
  (is (= "11" (text-content "[data-testid='result-value']")))
  (click! (testid "node-args-0"))
  (click! (testid "action-delete"))
  (is (= "Partial" (text-content "[data-testid='status-kind']")))
  (click! (testid "action-insert-literal-4"))
  (is (= "Success" (text-content "[data-testid='status-kind']")))
  (is (= "7" (text-content "[data-testid='result-value']"))))

(deftest restores-a-saved-session-on-remount
  (mount-app!)
  (click! (testid "action-insert-literal-3"))
  (app/unmount-shell!)
  (mount-app!)
  (is (= "Restored" (text-content "[data-testid='status-kind']")))
  (is (= "5" (text-content "[data-testid='result-value']")))
  (is (= "node-args-1" (selected-node-id))))

(deftest keyboard-navigation-and-error-recovery-work
  (mount-app!)
  (let [selected (testid "node-args-1")]
    (.focus selected)
    (keydown! selected "ArrowLeft"))
  (is (= "node-args-0" (selected-node-id)))
  (keydown! (testid "node-args-0") "ArrowUp")
  (is (= "node-root" (selected-node-id)))
  (keydown! (testid "node-root") "ArrowDown")
  (is (= "node-fn" (selected-node-id)))
  (keydown! (testid "node-fn") "ArrowRight")
  (is (= "node-args-0" (selected-node-id)))
  (click! (testid "node-args-1"))
  (click! (testid "action-insert-symbol-wat"))
  (is (= "Error" (text-content "[data-testid='status-kind']")))
  (is (= "This function does not exist yet."
         (text-content "[data-testid='result-value']")))
  (click! (testid "action-insert-literal-3"))
  (is (= "Success" (text-content "[data-testid='status-kind']")))
  (is (= "5" (text-content "[data-testid='result-value']"))))
