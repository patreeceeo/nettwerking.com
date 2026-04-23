(ns app.backend.main
  (:gen-class)
  (:require [app.backend.server :as server]))

(defn env-port
  "Returns the PORT environment variable as an integer when present."
  []
  (when-some [port-value (System/getenv "PORT")]
    (or (parse-long port-value)
        (throw (ex-info "Invalid PORT value"
                        {:port-value port-value})))))

(defn -main
  "Starts the backend server for the configured or default port."
  [& _args]
  (server/start-server {:port (or (env-port) 8080)
                        :join? true}))
