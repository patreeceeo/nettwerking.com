(ns app.frontend.test-runner
  {:dev/always true}
  (:require [cljs.test :as ct]
            [clojure.string :as string]
            [shadow.dom :as dom]
            [shadow.test.browser]
            [shadow.test :as st]
            [shadow.test.env :as env]))

(defonce failure-events (atom []))

(def empty-summary
  {:test 0
   :pass 0
   :fail 0
   :error 0})

(defn- summary-text [{:keys [test pass fail error]}]
  (str "Ran " (or test 0) " tests with "
       (or pass 0) " passes, "
       (or fail 0) " failures, and "
       (or error 0) " errors."))

(defn- status-element []
  (or (.getElementById js/document "frontend-test-status")
      (dom/append
       [:div#frontend-test-status
        {:data-testid "frontend-test-status"
         :data-test-status "running"
         :data-test-summary "Preparing frontend tests."}
        "Preparing frontend tests."])))

(defn- set-status! [status summary]
  (let [element (status-element)
        text (summary-text summary)
        details (if (seq @failure-events)
                  (string/join "\n" (map pr-str @failure-events))
                  "")]
    (.setAttribute element "data-test-status" status)
    (.setAttribute element "data-failures" (str (:fail summary 0)))
    (.setAttribute element "data-errors" (str (:error summary 0)))
    (.setAttribute element "data-tests" (str (:test summary 0)))
    (.setAttribute element "data-test-summary" text)
    (.setAttribute element "data-failure-details" details)
    (set! (.-textContent element) text)
    (set! (.-title js/document)
          (str "frontend-test: " status))
    (aset js/window "__frontendTestStatus"
          (clj->js {:status status
                    :summary summary}))))

(defn- report! [event]
  (case (:type event)
    :pass
    (ct/inc-report-counter! :pass)

    :fail
    (do
      (ct/inc-report-counter! :fail)
      (swap! failure-events conj (select-keys event [:expected :actual :message]))
      (js/console.error "FAIL" (pr-str (select-keys event [:expected :actual :message]))))

    :error
    (do
      (ct/inc-report-counter! :error)
      (swap! failure-events conj (select-keys event [:expected :actual :message]))
      (js/console.error "ERROR" (pr-str (select-keys event [:expected :actual :message]))))

    :begin-test-ns
    (js/console.log (str "Testing " (name (:ns event))))

    :summary
    (set-status! (if (ct/successful? event) "passed" "failed") event)

    :begin-run-tests
    (set-status! "running" empty-summary)

    nil))

(defn start []
  (-> (env/get-test-data)
      (env/reset-test-data!))
  (reset! failure-events [])
  (set-status! "running" empty-summary)
  (st/run-all-tests {:report-fn report!}))

(defn stop [done]
  (done))

(defn ^:export init []
  (start))

(set! shadow.test.browser/init init)
