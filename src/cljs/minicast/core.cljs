(ns minicast.core
    (:require-macros [cljs.core.async.macros :refer [go]])
    (:require [reagent.core :as reagent :refer [atom]]
              [reagent.session :as session]
              [secretary.core :as secretary :include-macros true]
              [goog.events :as events]
              [goog.history.EventType :as EventType]
              [ajax.core :refer [GET POST ajax-request json-response-format]]
              [cljs.core.async :refer [<!]])
    (:import goog.History))

(defonce app-state (atom nil))
(defonce errors (atom []))
(defonce auth-state (atom nil))

(def server-url "http://localhost:8000/server/")

;; -------------------------
;; Helper functions

; log a single error message
(defn log-error [e]
  (swap! errors conj e))

; if we hit an error loading an ajax endpoint
(defn ajax-error-handler [{:keys [status status-text]}]
  (log-error (str "Oops: " status " " status-text)))

; unified interface for access to our api
(defn api-request [endpoint callback]
  (ajax-request {:uri (str server-url endpoint ".php") :method :get :response-format (json-response-format) :handler
    (fn [[ok result]]
      (callback [ok result]))}))

; initiate the request for user's current state
(defn request-state []
  (api-request "state"
    (fn [[ok result]]
      (reset! auth-state (:response result)))))

;; -------------------------
;; Components

(defn component-errors []
  (if (not (empty? @errors))
    [:div {:class "errors" :on-click (fn [ev] (reset! errors nil))}
      (map-indexed (fn [i e] [:div {:class "error" :key (str "error-" i)} [:i {:class "fa fa-warning"}] e]) @errors)]))

(defn component-loader []
  [:div {:class "loader-inner line-scale-pulse-out-rapid"}
    [:div] [:div] [:div] [:div] [:div]])

(defn component-logo []
  [:div
    [:div {:class "fog"}]
    [:div {:class "logo"} "mini" [:b "Cast"]]])

(defn component-first-run []
  (let [un (atom "") pw (atom "")]
    [:div {:class "firstrun"}
     (component-logo)
     [:div
      [:p "To get started create a new username and password:"]
      [:input {:placeholder "username" :type "text" :value @un :on-change (fn [ev] (reset! un (-> ev .-target .-value)) (println @un))}]
      [:input {:type "password" :value @pw :placeholder "password" :on-change #(reset! pw (-> % .-target .-value))}]
      [:button "Go"]]]))

;; -------------------------
;; Views

(defn home-page []
  (fn []
    [:div
      ; display any errors received to the user
      (component-errors)
      ; we don't have authentication state or app-state yet
      (if (nil? @auth-state)
        [:div
            (component-logo)
            (component-loader)])
      (cond
        (= @auth-state "AUTH_NO_FILE") (component-first-run))
      [:div {:class "debug"} @auth-state]]))

(defn current-page []
  [:div [(session/get :current-page)]])

;; -------------------------
;; Routes

(secretary/set-config! :prefix "#")

(secretary/defroute "/" []
  (session/put! :current-page #'home-page))

;; -------------------------
;; History
;; must be called after routes have been defined

(defn hook-browser-navigation! []
  (doto (History.)
    (events/listen
     EventType/NAVIGATE
     (fn [event]
       (secretary/dispatch! (.-token event))))
    (.setEnabled true)))

;; -------------------------
;; Initialize app

(defn mount-root []
  (reagent/render [current-page] (.getElementById js/document "app")))

(defn init! []
  (hook-browser-navigation!)
  (mount-root))

; make the request to get our state from the server
(request-state)

