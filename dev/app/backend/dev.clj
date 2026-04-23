(ns app.backend.dev
  (:require [app.backend.server :as server]
            [clojure.tools.namespace.repl :as repl]))

(defonce server-instance (atom nil))

(defn start
  "Starts the development server if it is not already running."
  []
  (swap! server-instance
         (fn [running]
           (or running
               (server/start-server {:port 8080 :join? false})))))

(defn stop
  "Stops the running development server and clears the cached instance."
  []
  (when-let [running @server-instance]
    (.stop running)
    (reset! server-instance nil))
  :stopped)

(defn go
  "Starts the development system."
  []
  (start)
  :ready)

(defn reset
  "Stops the running system and reloads changed namespaces."
  []
  (stop)
  (repl/refresh :after 'app.backend.dev/go))
