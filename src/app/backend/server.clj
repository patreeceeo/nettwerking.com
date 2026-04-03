(ns app.backend.server
  (:require [reitit.ring :as ring]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.not-modified :refer [wrap-not-modified]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.util.response :as response]))

(defn index-handler [_request]
  (or (some-> (response/resource-response "public/index.html")
              (response/content-type "text/html; charset=utf-8"))
      {:status 404
       :headers {"content-type" "text/plain; charset=utf-8"}
       :body "Missing resources/public/index.html"}))

(defn health-handler [_request]
  {:status 200
   :headers {"content-type" "application/json; charset=utf-8"}
   :body "{\"status\":\"ok\"}"})

(def routes
  [["/" {:get index-handler}]
   ["/api/health" {:get health-handler}]])

(def app
  (-> (ring/ring-handler
       (ring/router routes)
       (ring/create-default-handler))
      (wrap-resource "public")
      wrap-content-type
      wrap-not-modified))

(defn start-server
  ([] (start-server {:port 8080 :join? false}))
  ([{:keys [port join?] :or {port 8080 join? false}}]
   (jetty/run-jetty #'app {:port port :join? join?})))
