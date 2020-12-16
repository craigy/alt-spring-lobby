(ns user
  (:require
    [cemerick.pomegranate :as pomegranate]
    [cemerick.pomegranate.aether :refer [maven-central]]
    [chime.core :as chime]
    [cljfx.api :as fx]
    [clojure.datafy :refer [datafy]]
    [clojure.java.io :as io]
    [clojure.pprint :refer [pprint]]
    [clojure.string :as string]
    [clojure.tools.namespace.repl :refer [disable-unload! disable-reload! refresh]]
    [hawk.core :as hawk]
    java-time
    [pjstadig.humane-test-output]))


(disable-unload!)
(disable-reload!)


(pjstadig.humane-test-output/activate!)


; application state, copy this to spring-lobby nses on refresh

(def *state (atom nil))

; state from init to clean up (chimers)

(def init-state (atom nil))

; prevent duplicate refreshes

(def refreshing (atom false))

; renderer var, create in init

(def ^:dynamic renderer nil)


(defn stop-chimer [chimer]
  (when chimer
    (try
      (chimer)
      (catch Exception e
        (println "error stopping chimer" chimer e)))))

(defn rerender []
  (try
    (println "Stopping old chimers")
    (let [{:keys [tasks-chimer file-events-chimer]} @init-state]
      (stop-chimer tasks-chimer)
      (stop-chimer file-events-chimer))
    (println "Requiring spring-lobby ns")
    (require 'spring-lobby)
    (alter-var-root (find-var 'spring-lobby/*state) (constantly *state))
    (future
      (try
        (let [init-fn (var-get (find-var 'spring-lobby/init))]
          (init-fn *state))
        (catch Exception e
          (println "init error" e))))
    (if renderer
      (do
        (println "Re-rendering")
        (try
          (renderer)
          (catch Exception e
            (println "error rendering" e))))
      (println "No renderer"))
    (catch Exception e
      (println e))))

(defn refresh-rerender []
  (println "Refreshing")
  (future
    (try
      (binding [*ns* *ns*]
        (println (refresh :after 'user/rerender)))
      (catch Exception e
        (println e))
      (finally
        (reset! refreshing false)))))

(defn refresh-on-file-change [context event]
  (when-let [file (:file event)]
    (let [f (io/file file)]
      (when (and (.exists f) (not (.isDirectory f)))
        (if @refreshing
          (println "Duplicate file event, skipping refresh")
          (try
            (reset! refreshing true)
            (refresh-rerender)
            (catch Exception e
              (println e)))))))
  context)


(defn view [state]
  (require 'spring-lobby)
  (let [actual-view (var-get (find-var 'spring-lobby/root-view))]
    (actual-view state)))

(defn event-handler [e]
  (require 'spring-lobby)
  (let [actual-handler (var-get (find-var 'spring-lobby/event-handler))]
    (actual-handler e)))


(defn init []
  (try
    [datafy pprint chime/chime-at string/split]
    (hawk/watch! [{:paths ["src/clj"]
                   :handler refresh-on-file-change}])
    (require 'spring-lobby)
    (require 'spring-lobby.fs)
    (let [init-7z-fn (var-get (find-var 'spring-lobby.fs/init-7z!))]
      (future
        (try
          (println "Initializing 7zip")
          (init-7z-fn)
          (println "Finally finished initializing 7zip")
          (catch Exception e
            (println e)))))
    (alter-var-root #'*state (constantly (var-get (find-var 'spring-lobby/*state))))
    ; just use spring-lobby/*state for initial state, on refresh copy user/*state var back
    (let [init-fn (var-get (find-var 'spring-lobby/init))
          r (fx/create-renderer
              :middleware (fx/wrap-map-desc
                            (fn [state]
                              {:fx/type view
                               :state state}))
              :opts {:fx.opt/map-event-handler event-handler})]
      (reset! init-state (init-fn *state))
      (alter-var-root #'renderer (constantly r)))
    (fx/mount-renderer *state renderer)
    (catch Exception e
      (println e))))


(defn add-dependencies [coordinates]
  (pomegranate/add-dependencies
    :coordinates coordinates
    :repositories (merge maven-central
                         {"clojars" "https://clojars.org/repo"})))
