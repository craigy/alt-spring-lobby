(ns spring-lobby
  (:require
    [cljfx.api :as fx]
    [cljfx.ext.node :as fx.ext.node]
    [cljfx.ext.table-view :as fx.ext.table-view]
    [clojure.core.async :as async]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.pprint :refer [pprint]]
    [clojure.string :as string]
    [com.evocomputing.colors :as colors]
    [spring-lobby.client :as client]
    [spring-lobby.fx.font-icon :as font-icon]
    [spring-lobby.fs :as fs]
    [spring-lobby.spring :as spring]
    [taoensso.timbre :as log])
  (:import
    (javafx.scene.paint Color)
    (manifold.stream SplicedStream)))


(set! *warn-on-reflection* true)


(def default-state
  {:username "skynet9001"
   :password "1234dogs"
   :map-name "Dworld Acidic"
   :mod-name "Balanced Annihilation V9.79.4"
   :title "deth"
   :engine-version "103.0"})

(def ^:dynamic *state
  (atom default-state))


(defmulti event-handler :event/type)


(defmethod event-handler ::reload-maps [_e]
  (future
    (swap! *state assoc :maps-cached nil)
    (swap! *state assoc :maps-cached (doall (fs/maps)))))


(defn menu-view [_opts]
  {:fx/type :menu-bar
   :menus
   [{:fx/type :menu
     :text "Server"
     :items [{:fx/type :menu-item
              :text "Connect"}
             {:fx/type :menu-item
              :text "Disconnect"}
             {:fx/type :menu-item
              :text "Pick Server"}]}
    {:fx/type :menu
     :text "Edit"
     :items [{:fx/type :menu-item
              :text "menu2 item1"}
             {:fx/type :menu-item
              :text "menu2 item2"}]}
    {:fx/type :menu
     :text "Tools"
     :items [{:fx/type :menu-item
              :text "menu3 item1"}
             {:fx/type :menu-item
              :text "menu3 item2"}]}
    {:fx/type :menu
     :text "Help"
     :items [{:fx/type :menu-item
              :text "menu4 item1"}
             {:fx/type :menu-item
              :text "menu4 item2"}]}]})

(defmethod event-handler ::select-battle [e]
  (swap! *state assoc :selected-battle (-> e :fx/event :battle-id)))

(defn battles-table [{:keys [battles users]}]
  {:fx/type fx.ext.table-view/with-selection-props
   :props {:selection-mode :single
           :on-selected-item-changed {:event/type ::select-battle}}
   :desc
   {:fx/type :table-view
    :column-resize-policy :constrained ; TODO auto resize
    :items (vec (vals battles))
    :style {:-fx-max-height "240px"}
    :columns
    [{:fx/type :table-column
      :text "Battle Name"
      :cell-value-factory identity
      :cell-factory
      {:fx/cell-type :table-cell
       :describe (fn [i] {:text (str (:battle-title i))})}}
     {:fx/type :table-column
      :text "Host"
      :cell-value-factory identity
      :cell-factory
      {:fx/cell-type :table-cell
       :describe (fn [i] {:text (str (:host-username i))})}}
     {:fx/type :table-column
      :text "Status"
      :cell-value-factory identity
      :cell-factory
      {:fx/cell-type :table-cell
       :describe
       (fn [i]
         (let [status (select-keys i [:battle-type :battle-passworded])]
           (if (:battle-passworded status)
             {:text ""
              :graphic
              {:fx/type font-icon/lifecycle
               :icon-literal "mdi-lock:16"}}
             {:text ""
              :graphic
              {:fx/type font-icon/lifecycle
               :icon-literal "mdi-lock-open:16"}})))}}
     {:fx/type :table-column
      :text "Country"
      :cell-value-factory identity
      :cell-factory
      {:fx/cell-type :table-cell
       :describe (fn [i] {:text (str (:country (get users (:host-username i))))})}}
     {:fx/type :table-column
      :text "?"
      :cell-value-factory identity
      :cell-factory
      {:fx/cell-type :table-cell
       :describe (fn [i] {:text (str (:battle-rank i))})}}
     {:fx/type :table-column
      :text "Players"
      :cell-value-factory identity
      :cell-factory
      {:fx/cell-type :table-cell
       :describe (fn [i] {:text (str (count (:users i)))})}}
     {:fx/type :table-column
      :text "Max"
      :cell-value-factory identity
      :cell-factory
      {:fx/cell-type :table-cell
       :describe (fn [i] {:text (str (:battle-maxplayers i))})}}
     {:fx/type :table-column
      :text "Spectators"
      :cell-value-factory identity
      :cell-factory
      {:fx/cell-type :table-cell
       :describe (fn [i] {:text (str (:battle-spectators i))})}}
     {:fx/type :table-column
      :text "Running"
      :cell-value-factory identity
      :cell-factory
      {:fx/cell-type :table-cell
       :describe (fn [i] {:text (->> i :host-username (get users) :client-status :ingame str)})}}
     {:fx/type :table-column
      :text "Game"
      :cell-value-factory identity
      :cell-factory
      {:fx/cell-type :table-cell
       :describe (fn [i] {:text (str (:battle-modname i))})}}
     {:fx/type :table-column
      :text "Map"
      :cell-value-factory identity
      :cell-factory
      {:fx/cell-type :table-cell
       :describe (fn [i] {:text (str (:battle-map i))})}}
     {:fx/type :table-column
      :text "Engine"
      :cell-value-factory identity
      :cell-factory
      {:fx/cell-type :table-cell
       :describe (fn [i] {:text (str (:battle-engine i) " " (:battle-version i))})}}]}})

(defn user-table [{:keys [users]}]
  {:fx/type :table-view
   :column-resize-policy :constrained ; TODO auto resize
   :items (vec (vals users))
   :style {:-fx-max-height "240px"}
   :columns
   [{:fx/type :table-column
     :text "Username"
     :cell-value-factory identity
     :cell-factory
     {:fx/cell-type :table-cell
      :describe (fn [i] {:text (str (:username i))})}}
    {:fx/type :table-column
     :text "Status"
     :cell-value-factory identity
     :cell-factory
     {:fx/cell-type :table-cell
      :describe
      (fn [i]
        (let [status (select-keys (:client-status i) [:bot :access :away :ingame])]
          (cond
            (:bot status)
            {:text ""
             :graphic
             {:fx/type font-icon/lifecycle
              :icon-literal "mdi-robot:16"}}
            (:away status)
            {:text ""
             :graphic
             {:fx/type font-icon/lifecycle
              :icon-literal "mdi-sleep:16"}}
            (:access status)
            {:text ""
             :graphic
             {:fx/type font-icon/lifecycle
              :icon-literal "mdi-account-key:16"}}
            :else
            {:text ""
             :graphic
             {:fx/type font-icon/lifecycle
              :icon-literal "mdi-account:16"}}
            #_
            {:text (str status)
             :style {:-fx-font-family "monospace"}})))}}
    {:fx/type :table-column
     :text "Country"
     :cell-value-factory identity
     :cell-factory
     {:fx/cell-type :table-cell
      :describe (fn [i] {:text (str (:country i))})}}
    {:fx/type :table-column
     :text "Rank"
     :cell-value-factory identity
     :cell-factory
     {:fx/cell-type :table-cell
      :describe (fn [i] {:text (str (:rank (:client-status i)))})}}
    {:fx/type :table-column
     :text "Lobby Client"
     :cell-value-factory identity
     :cell-factory
     {:fx/cell-type :table-cell
      :describe (fn [i] {:text (str (:user-agent i))})}}]})

(defn update-disconnected []
  (log/warn (ex-info "stacktrace" {}) "Updating state after disconnect")
  (swap! *state dissoc
         :battle :battles :channels :client :client-deferred :my-channels :users
         :last-failed-message)
  nil)

(defmethod event-handler ::print-state [_e]
  (pprint *state))

(defmethod event-handler ::disconnect [_e]
  (when-let [client (:client @*state)]
    (client/disconnect client))
  (update-disconnected))

(defn connected-loop [state-atom client-deferred]
  (swap! state-atom assoc
         :connected-loop
         (future
           (try
             (let [^SplicedStream client @client-deferred]
               (client/connect state-atom client) ; TODO username password
               (swap! state-atom assoc :client client :login-error nil)
               (loop []
                 (if (and client (not (.isClosed client)))
                   (when-not (Thread/interrupted)
                     (log/debug "Client is still connected")
                     (async/<!! (async/timeout 20000))
                     (recur))
                   (when-not (Thread/interrupted)
                     (log/info "Client was disconnected")
                     (update-disconnected))))
               (log/info "Connect loop closed"))
             (catch Exception e
               (log/error e "Connect loop error")
               (when-not (or (Thread/interrupted) (instance? java.lang.InterruptedException e))
                 (swap! state-atom assoc :login-error (str (.getMessage e)))
                 (update-disconnected))))
           nil)))

(defmethod event-handler ::connect [_e]
  (let [client-deferred (client/client)] ; TODO host port
    (swap! *state assoc :client-deferred client-deferred)
    (connected-loop *state client-deferred)))


(defn client-buttons [{:keys [client client-deferred username password login-error]}]
  {:fx/type :h-box
   :alignment :top-left
   :children
   [{:fx/type :button
     :text (if client
             "Disconnect"
             (if client-deferred
               "Connecting..."
               "Connect"))
     :disable (boolean (and (not client) client-deferred))
     :on-action {:event/type (if client ::disconnect ::connect)}}
    {:fx/type :text-field
     :text username
     :disable (boolean (or client client-deferred))
     :on-action {:event/type ::username-change}}
    {:fx/type :password-field
     :text password
     :disable (boolean (or client client-deferred))
     :on-action {:event/type ::password-change}}
    {:fx/type :v-box
     :h-box/hgrow :always
     :alignment :center
     :children
     [{:fx/type :label
       :text (str login-error)
       :style {:-fx-text-fill "#FF0000"
               :-fx-max-width "360px"}}]}
    {:fx/type :button
     :text "Print state"
     :on-action {:event/type ::print-state}
     :alignment :top-right}]})

(defmethod event-handler ::username-change [e]
  (swap! *state assoc :username (:fx/event e)))

(defmethod event-handler ::password-change [e]
  (swap! *state assoc :password (:fx/event e)))


(defn host-battle []
  (let [{:keys [client] :as state} @*state]
    (client/open-battle client
      (assoc
        (select-keys state [:battle-password :title :engine-version :mod-name :map-name])
        :mod-hash -1
        :map-hash -1))))

(defmethod event-handler ::host-battle [_e]
  (host-battle))


(defmethod event-handler ::leave-battle [_e]
  (client/send-message (:client @*state) "LEAVEBATTLE"))

(defmethod event-handler ::join-battle [_e]
  (let [{:keys [battles battle-password selected-battle]} @*state]
    (when selected-battle
      (client/send-message (:client @*state)
        (str "JOINBATTLE " selected-battle
             (when (= "1" (-> battles (get selected-battle) :battle-passworded)) ; TODO
               (str " " battle-password)))))))

(defn map-list
  [{:keys [disable map-name maps-cached on-value-changed]}]
  (if maps-cached
    (let [map-names (mapv :map-name maps-cached)]
      {:fx/type :h-box
       :children
       [{:fx/type :choice-box
         :value (str map-name)
         :items map-names
         :disable (boolean disable)
         :on-value-changed on-value-changed}
        {:fx/type fx.ext.node/with-tooltip-props
         :props
         {:tooltip
          {:fx/type :tooltip
           :show-delay [10 :ms]
           :text "Random map"}}
         :desc
         {:fx/type :button
          :on-action (assoc on-value-changed :map-name (rand-nth map-names))
          :graphic
          {:fx/type font-icon/lifecycle
           :icon-literal (str "mdi-dice-" (inc (rand-nth (take 6 (iterate inc 0)))) ":16:white")}}}
        {:fx/type fx.ext.node/with-tooltip-props
         :props
         {:tooltip
          {:fx/type :tooltip
           :show-delay [10 :ms]
           :text "Reload maps"}}
         :desc
         {:fx/type :button
          :on-action {:event/type ::reload-maps}
          :graphic
          {:fx/type font-icon/lifecycle
           :icon-literal "mdi-refresh:16:white"}}}]})
    {:fx/type :v-box
     :alignment :center-left
     :children
     [{:fx/type :label
       :text "Loading maps..."}]}))

(defn battles-buttons
  [{:keys [battle battles battle-password client selected-battle title engine-version mod-name map-name maps-cached]}]
  {:fx/type :h-box
   :alignment :top-left
   :children
   (concat
     (when battle
       [{:fx/type :button
         :text "Leave Battle"
         :on-action {:event/type ::leave-battle}}])
     (when (and (not battle) selected-battle (-> battles (get selected-battle)))
       (let [needs-password (= "1" (-> battles (get selected-battle) :battle-passworded))] ; TODO
         [{:fx/type :button
           :text "Join Battle"
           :disable (boolean (and needs-password (string/blank? battle-password)))
           :on-action {:event/type ::join-battle}}]))
     (when (and client (not battle))
       [{:fx/type :text-field
         :text (str battle-password)
         :prompt-text "Battle Password"
         :on-action {:event/type ::host-battle}
         :on-text-changed {:event/type ::battle-password-change}}
        {:fx/type :button
         :text "Host Battle"
         :on-action {:event/type ::host-battle}}
        {:fx/type :text-field
         :text (str title)
         :prompt-text "Battle Title"
         :on-action {:event/type ::host-battle}
         :on-text-changed {:event/type ::title-change}}])
     (when (not battle)
       [{:fx/type :choice-box
         :value (str engine-version)
         :items (fs/engines)
         :on-value-changed {:event/type ::version-change}}
        {:fx/type :choice-box
         :value (str mod-name)
         :items (->> (fs/games)
                     (map :modinfo)
                     (map (fn [modinfo] (str (:name modinfo) " " (:version modinfo)))))
         :on-value-changed {:event/type ::mod-change}}
        {:fx/type map-list
         :map-name map-name
         :maps-cached maps-cached
         :on-value-changed {:event/type ::map-change}}]))})


(defmethod event-handler ::battle-password-change [e]
  (swap! *state assoc :battle-password (:fx/event e)))

(defmethod event-handler ::title [e]
  (swap! *state assoc :title (:fx/event e)))

(defmethod event-handler ::version-change [e]
  (swap! *state assoc :engine-version (:fx/event e)))

(defmethod event-handler ::mod-change [e]
  (swap! *state assoc :mod-name (:fx/event e)))

(defmethod event-handler ::map-change
  [{:fx/keys [event] :keys [map-name]}]
  (swap! *state assoc :map-name (or map-name event)))

(defmethod event-handler ::battle-map-change
  [{:fx/keys [event] :keys [map-name]}]
  (let [spectator-count 0 ; TODO
        locked 0
        map-hash -1 ; TODO
        map-name (or map-name event)
        m (str "UPDATEBATTLEINFO " spectator-count " " locked " " map-hash " " map-name)]
    (client/send-message (:client @*state) m)))

(defmethod event-handler ::kick-battle
  [{:keys [is-bot username]}]
  (when-let [client (:client @*state)]
    (if is-bot
      (client/send-message client (str "REMOVEBOT " username))
      (client/send-message client (str "KICKFROMBATTLE " username)))))


; doesn't work from WSL
(defn spring-env []
  (into-array String ["SPRING_WRITEDIR=C:\\Users\\craig\\.alt-spring-lobby\\spring\\write"
                      "SPRING_DATADIR=C:\\Users\\craig\\.alt-spring-lobby\\spring\\data"]))

(defn random-color
  []
  (long (rand (* 255 255 255))))

(defn available-name [existing-names desired-name]
  (if-not (contains? (set existing-names) desired-name)
    desired-name
    (recur
      existing-names
      (if-let [[_ prefix n suffix] (re-find #"(.*)(\d+)(.*)" desired-name)]
        (let [nn (inc (edn/read-string n))]
          (str prefix nn suffix))
        (str desired-name 0)))))

(defmethod event-handler ::add-bot [{:keys [battle bot-username bot-name bot-version]}]
  (let [existing-bots (keys (:bots battle))
        bot-username (available-name existing-bots bot-username)
        bot-status (client/encode-battle-status
                     (assoc client/default-battle-status
                            :ready true
                            :mode 1
                            :sync 1
                            :side (rand-nth [0 1])))
        bot-color (random-color)
        message (str "ADDBOT " bot-username " " bot-status " " bot-color " " bot-name "|" bot-version)]
    (client/send-message (:client @*state) message)))

(defmethod event-handler ::change-bot-username
  [{:fx/keys [event]}]
  (swap! *state assoc :bot-username event))

(defmethod event-handler ::change-bot-name
  [{:keys [engine-version] :fx/keys [event]}]
  (let [bot-name event
        bot-version (-> (group-by :bot-name (fs/bots engine-version))
                        (get bot-name)
                        first
                        :bot-version)]
    (swap! *state assoc :bot-name bot-name :bot-version bot-version)))

(defmethod event-handler ::change-bot-version
  [{:fx/keys [event]}]
  (swap! *state assoc :bot-version event))


(defn script-txt []
  (let [{:keys [battle battles users username]} @*state
        battle (update battle :users #(into {} (map (fn [[k v]] [k (assoc v :username k :user (get users k))]) %)))
        battle (merge (get battles (:battle-id battle)) battle)
        script (spring/script-data battle {:myplayername username})
        script-txt (spring/script-txt script)]
    script-txt))

(defn start-game []
  (try
    (log/info "Starting game")
    (let [state @*state
          battle-version (-> state
                             :battles
                             (get (-> state :battle :battle-id))
                             :battle-version)
          script-txt (script-txt)
          engine-file (io/file (fs/spring-root) "engine" battle-version "spring.exe")
          _ (log/info "Engine executable" engine-file)
          ;script-file (io/file (fs/spring-root) "script.txt")
          ;homedir (io/file (System/getProperty "user.home"))
          ;script-file (io/file homedir "script.txt")
          script-file (io/file "/mnt/c/Users/craig/.alt-spring-lobby/spring/script.txt") ; TODO remove
          isolation-dir (io/file "/mnt/c/Users/craig/.alt-spring-lobby/spring/engine" battle-version)] ; TODO remove
          ;homedir (io/file "C:\\Users\\craig\\.alt-spring-lobby\\spring") ; TODO remove
      (spit script-file script-txt)
      (log/info "Wrote script to" script-file)
      (let [command [(.getAbsolutePath engine-file)
                     "--isolation-dir" (str "C:\\Users\\craig\\.alt-spring-lobby\\spring\\engine\\" battle-version) ; TODO windows path
                     "C:\\Users\\craig\\.alt-spring-lobby\\spring\\script.txt"] ; TODO windows path
            runtime (Runtime/getRuntime)]
        (log/info "Running '" command "'")
        (let [^"[Ljava.lang.String;" cmdarray (into-array String command)
              ^"[Ljava.lang.String;" envp nil
              process (.exec runtime cmdarray envp isolation-dir)]
          (client/send-message (:client @*state) "MYSTATUS 1") ; TODO full status
          (async/thread
            (with-open [^java.io.BufferedReader reader (io/reader (.getInputStream process))]
              (loop []
                (if-let [line (.readLine reader)]
                  (do
                    (log/info "(spring out)" line)
                    (recur))
                  (log/info "Spring stdout stream closed")))))
          (async/thread
            (with-open [^java.io.BufferedReader reader (io/reader (.getErrorStream process))]
              (loop []
                (if-let [line (.readLine reader)]
                  (do
                    (log/info "(spring err)" line)
                    (recur))
                  (log/info "Spring stderr stream closed")))))
          (future
            (.waitFor process)
            (client/send-message (:client @*state) "MYSTATUS 0"))))) ; TODO full status
    (catch Exception e
      (log/error e "Error starting game")
      (client/send-message (:client @*state) "MYSTATUS 0")))) ; TODO full status

(defmethod event-handler ::start-battle [_e]
  (start-game))



(def start-pos-r 10.0)
(def map-multiplier 8.0)
(def minimap-width 256)
(def minimap-height 256)


(defn minimap-starting-points [map-details]
  (let [{:keys [map-width map-height]} (-> map-details :smf :header)
        teams (or (-> map-details :mapinfo :teams)
                  (->> map-details
                       :smd
                       :map
                       (filter (comp #(string/starts-with? % "team") name first))
                       (map
                         (fn [[k {:keys [startposx startposz]}]]
                           [k {:startpos {:x startposx :z startposz}}]))
                       (into {})))]
    (map
      (fn [[_team {:keys [startpos]}]]
        (let [{:keys [x z]} startpos]
          {:x (- (* (/ x (* map-multiplier map-width)) minimap-width)
                 (/ start-pos-r 2))
           :y (- (* (/ z (* map-multiplier map-height)) minimap-height)
                 (/ start-pos-r 2))}))
      teams)))

(defn battle-buttons
  [{:keys [am-host host-user host-username maps-cached battle-map bot-username bot-name bot-version
           engine-version map-details battle username users]}]
  {:fx/type :h-box
   :children
   (concat
     [{:fx/type :v-box
       :children
       [{:fx/type :h-box
         :alignment :top-left
         :children
         [{:fx/type fx.ext.node/with-tooltip-props
           :props
           {:tooltip
            {:fx/type :tooltip
             :show-delay [10 :ms]
             :text (if am-host
                     "You are the host"
                     (str "Waiting for host " host-username))}}
           :desc
           {:fx/type :button
            :text (str (if am-host "Start" "Join") " Game")
            :disable (boolean (and (not am-host)
                                   (not (-> host-user :client-status :ingame))))
            :on-action {:event/type ::start-battle}}}
          {:fx/type map-list
           :disable (not am-host)
           :map-name battle-map
           :maps-cached maps-cached
           :on-value-changed {:event/type ::battle-map-change}}]}
        {:fx/type :h-box
         :alignment :top-left
         :children
         [{:fx/type :button
           :text "Add Bot"
           :disable (or (string/blank? bot-username)
                        (string/blank? bot-name)
                        (string/blank? bot-version))
           :on-action {:event/type ::add-bot
                       :battle battle
                       :bot-username bot-username
                       :bot-name bot-name
                       :bot-version bot-version}}
          {:fx/type :text-field
           :text (str bot-username)
           :on-text-changed {:event/type ::change-bot-username}}
          {:fx/type :choice-box
           :value (str bot-name)
           :on-value-changed {:event/type ::change-bot-name
                              :engine-version engine-version}
           :items (map :bot-name (fs/bots engine-version))}
          {:fx/type :choice-box
           :value (str bot-version)
           :on-value-changed {:event/type ::change-bot-version}
           :items (map :bot-version
                       (or (get (group-by :bot-name (fs/bots engine-version))
                                bot-name)
                           []))}]}
        {:fx/type :pane
         :v-box/vgrow :always}
        {:fx/type :h-box
         :alignment :center-left
         :children
         [{:fx/type :button
           :text "Randomize Colors"
           :on-action {:event/type ::battle-randomize-colors
                       :battle battle
                       :users users
                       :username username}}]}
        {:fx/type :pane
         :v-box/vgrow :always}
        {:fx/type :h-box
         :alignment :center-left
         :children
         [{:fx/type :button
           :text "FFA"
           :on-action {:event/type ::battle-teams-ffa
                       :battle battle
                       :users users
                       :username username}}
          {:fx/type :button
           :text "2 teams"
           :on-action {:event/type ::battle-teams-2
                       :battle battle
                       :users users
                       :username username}}
          {:fx/type :button
           :text "3 teams"
           :on-action {:event/type ::battle-teams-3
                       :battle battle
                       :users users
                       :username username}}
          {:fx/type :button
           :text "4 teams"
           :on-action {:event/type ::battle-teams-4
                       :battle battle
                       :users users
                       :username username}}]}
        {:fx/type :pane
         :v-box/vgrow :always}
        {:fx/type :h-box
         :alignment :center-left
         :children
         [{:fx/type :label
           :alignment :center-left
           :text "Start Positions: "}
          {:fx/type :choice-box
           :value (->> battle
                       :scripttags
                       :game
                       :startpostype
                       (or 1)
                       (get spring/startpostypes)
                       str)
           :items (map str (vals spring/startpostypes))
           :disable (not am-host)
           :on-value-changed {:event/type ::battle-startpostype-change}}]}
        {:fx/type :pane
         :v-box/vgrow :always}
        {:fx/type :h-box
         :alignment :center-left
         :style {:-fx-font-size 24}
         :children
         [(let [{:keys [battle-status] :as me} (-> battle :users (get username))]
            {:fx/type :check-box
             :selected (-> battle-status :ready boolean)
             :style {:-fx-padding "10px"}
             :on-selected-changed (merge me
                                    {:event/type ::battle-ready-change
                                     :username username})})
          {:fx/type :label
           :alignment :center-left
           :text " Ready"}]}]}
      {:fx/type :pane
       :h-box/hgrow :always}
      {:fx/type :v-box
       :alignment :top-left
       :children
       [{:fx/type :label
         :text "scripttags"}
        {:fx/type :text-area
         :editable false
         :text (with-out-str (pprint (:scripttags battle)))
         :style {:-fx-font-family "monospace"}
         :v-box/vgrow :always}]}
      {:fx/type :v-box
       :alignment :top-left
       :children
       [{:fx/type :label
         :text "script.txt preview"}
        {:fx/type :text-area
         :editable false
         :text (str (string/replace (script-txt) #"\t" "  "))
         :style {:-fx-font-family "monospace"}
         :v-box/vgrow :always}]}
      {:fx/type :v-box
       :alignment :top-left
       :children
       [{:fx/type :label
         :text "Map Details"}
        {:fx/type :text-area
         :editable false
         :text (with-out-str (pprint map-details))
         :style {:-fx-font-family "monospace"}
         :v-box/vgrow :always}]}]
    (let [image-file (io/file (fs/map-minimap battle-map))]
      (when (.exists image-file)
        [{:fx/type :v-box
          :alignment :top-left
          :children
          [{:fx/type :label
            :text "Minimap"}
           {:fx/type :stack-pane
            :children
            (concat
              [{:fx/type :image-view
                :image {:is (let [image-file (io/file (fs/map-minimap battle-map))]
                              (when (.exists image-file)
                                (io/input-stream image-file)))}
                :fit-width minimap-width
                :fit-height minimap-height}
               {:fx/type :canvas
                :width minimap-width
                :height minimap-height
                :draw
                (fn [canvas]
                  (let [gc (.getGraphicsContext2D canvas)
                        starting-points (minimap-starting-points map-details)]
                    (.clearRect gc 0 0 minimap-width minimap-height)
                    (.setFill gc Color/RED)
                    (doseq [{:keys [x y]} starting-points]
                      (.fillOval gc x y start-pos-r start-pos-r))))}])}]}])))})


(defn battle-players-and-bots
  "Returns the sequence of all players and bots for a battle."
  [{:keys [battle users]}]
  (concat
    (mapv
      (fn [[k v]] (assoc v :username k :user (get users k)))
      (:users battle))
    (mapv
      (fn [[k v]]
        (assoc v
               :bot-name k
               :user {:client-status {:bot true}}))
      (:bots battle))))


(defn update-battle-status
  "Sends a message to update battle status for yourself or a bot of yours."
  [{:keys [client]} {:keys [is-bot id]} battle-status team-color]
  (when client
    (let [player-name (or (:bot-name id) (:username id))
          prefix (if is-bot
                   (str "UPDATEBOT " player-name) ; TODO normalize
                   "MYBATTLESTATUS")]
      (log/debug player-name (pr-str battle-status) team-color)
      (client/send-message client
        (str prefix
             " "
             (client/encode-battle-status battle-status)
             " "
             team-color)))))

(defn update-color [id {:keys [is-me is-bot] :as opts} color-int]
  (if (or is-me is-bot)
    (update-battle-status @*state (assoc opts :id id) (:battle-status id) color-int)
    (client/send-message (:client @*state)
      (str "FORCETEAMCOLOR " (:username id) " " color-int))))

(defn update-team [id {:keys [is-me is-bot] :as opts} player-id]
  (if (or is-me is-bot)
    (update-battle-status @*state (assoc opts :id id) (assoc (:battle-status id) :id player-id) (:team-color id))
    (client/send-message (:client @*state)
      (str "FORCETEAMNO " (:username id) " " player-id))))

(defn update-ally [id {:keys [is-me is-bot] :as opts} ally]
  (if (or is-me is-bot)
    (update-battle-status @*state (assoc opts :id id) (assoc (:battle-status id) :ally ally) (:team-color id))
    (client/send-message (:client @*state)
      (str "FORCEALLYNO " (:username id) " " ally))))

(defn apply-battle-status-changes
  [id {:keys [is-me is-bot] :as opts} status-changes]
  (if (or is-me is-bot)
    (update-battle-status @*state (assoc opts :id id) (merge (:battle-status id) status-changes) (:team-color id))
    (doseq [[k v] status-changes]
      (let [msg (case k
                  :id "FORCETEAMNO"
                  :ally "FORCEALLYNO")]
        (client/send-message (:client @*state) (str msg " " (:username id) " " v))))))


(defmethod event-handler ::battle-randomize-colors
  [e]
  (let [players-and-bots (battle-players-and-bots e)]
    (doseq [id players-and-bots]
      (let [is-bot (boolean (:bot-name id))
            is-me (= (:username e) (:username id))]
        (update-color id {:is-me is-me :is-bot is-bot} (random-color))))))

(defmethod event-handler ::battle-teams-ffa
  [e]
  (let [players-and-bots (battle-players-and-bots e)]
    (doall
      (map-indexed
        (fn [i id]
          (let [is-bot (boolean (:bot-name id))
                is-me (= (:username e) (:username id))]
            (apply-battle-status-changes id {:is-me is-me :is-bot is-bot} {:id i :ally i})))
        players-and-bots))))

(defn n-teams [e n]
  (let [players-and-bots (battle-players-and-bots e)
        per-partition (int (Math/ceil (/ (count players-and-bots) n)))
        by-ally (->> players-and-bots
                     (shuffle)
                     (map-indexed vector)
                     (partition-all per-partition)
                     vec)]
    (log/debug by-ally)
    (doall
      (map-indexed
        (fn [a players]
          (log/debug a (pr-str players))
          (doall
            (map
              (fn [[i id]]
                (let [is-bot (boolean (:bot-name id))
                      is-me (= (:username e) (:username id))]
                  (apply-battle-status-changes id {:is-me is-me :is-bot is-bot} {:id i :ally a})))
              players)))
        by-ally))))

(defmethod event-handler ::battle-teams-2
  [e]
  (n-teams e 2))

(defmethod event-handler ::battle-teams-3
  [e]
  (n-teams e 3))

(defmethod event-handler ::battle-teams-4
  [e]
  (n-teams e 4))


(defn fix-color
  "Returns the rgb int color represention for the given Spring bgr int color."
  [spring-color-int]
  (let [[r g b _a] (:rgba (colors/create-color spring-color-int))
        reversed (colors/create-color
                   {:r b
                    :g g
                    :b r})
        spring-int (colors/rgb-int reversed)]
    spring-int))

(defn spring-color
  "Returns the spring bgr int color format from a javafx color."
  [^javafx.scene.paint.Color color]
  (colors/rgba-int
    (colors/create-color
      {:r (Math/round (* 255 (.getBlue color)))  ; switch blue to red
       :g (Math/round (* 255 (.getGreen color)))
       :b (Math/round (* 255 (.getRed color)))   ; switch red to blue
       :a 0})))

#_
(colors/create-color
  (colors/rgb-int-to-components 0))
#_
(colors/create-color 0)


(defn battle-table
  [{:keys [battle battles users username maps-cached] :as state}]
  (let [{:keys [host-username battle-map]} (get battles (:battle-id battle))
        host-user (get users host-username)
        am-host (= username host-username)
        map-details (->> maps-cached
                         (filter (comp #{battle-map} :map-name))
                         first)]
    {:fx/type :v-box
     :alignment :top-left
     :children
     (concat
       [{:fx/type :table-view
         :column-resize-policy :constrained ; TODO auto resize
         :items (battle-players-and-bots state)
         :columns
         [{:fx/type :table-column
           :text "Nickname"
           :cell-value-factory identity
           :cell-factory
           {:fx/cell-type :table-cell
            :describe
            (fn [{:keys [bot-name owner] :as id}]
              (merge
                {:text (if bot-name
                         (str bot-name (when owner (str " (" owner ")")))
                         (str (:username id)))}
                (when (and (not= username (:username id))
                           (or am-host
                               (= owner username)))
                  {:graphic
                   {:fx/type :button
                    :on-action {:event/type ::kick-battle
                                :username (:username id)
                                :is-bot (-> id :user :client-status :bot)}
                    :graphic
                    {:fx/type font-icon/lifecycle
                     :icon-literal "mdi-account-remove:16:white"}}})))}}
          {:fx/type :table-column
           :text "Country"
           :cell-value-factory identity
           :cell-factory
           {:fx/cell-type :table-cell
            :describe (fn [i] {:text (str (:country (:user i)))})}}
          {:fx/type :table-column
           :text "Status"
           :cell-value-factory identity
           :cell-factory
           {:fx/cell-type :table-cell
            :describe
            (fn [i]
              (let [status (merge
                             (select-keys (:client-status (:user i)) [:bot])
                             (select-keys (:battle-status i) [:ready])
                             {:host (= (:username i) host-username)})]
                (cond
                  (:bot status)
                  {:text ""
                   :graphic
                   {:fx/type font-icon/lifecycle
                    :icon-literal "mdi-robot:16"}}
                  (:ready status)
                  {:text ""
                   :graphic
                   {:fx/type font-icon/lifecycle
                    :icon-literal "mdi-account-check:16"}}
                  (:host status)
                  {:text ""
                   :graphic
                   {:fx/type font-icon/lifecycle
                    :icon-literal "mdi-account-key:16"}}
                  :else
                  {:text ""
                   :graphic
                   {:fx/type font-icon/lifecycle
                    :icon-literal "mdi-account:16"}})))}}
          {:fx/type :table-column
           :text "Ingame"
           :cell-value-factory identity
           :cell-factory
           {:fx/cell-type :table-cell
            :describe (fn [i] {:text (str (:ingame (:client-status (:user i))))})}}
          {:fx/type :table-column
           :text "Spectator"
           :cell-value-factory identity
           :cell-factory
           {:fx/cell-type :table-cell
            :describe
            (fn [i]
              {:text ""
               :graphic
               {:fx/type :check-box
                :selected (not (:mode (:battle-status i)))
                :on-selected-changed {:event/type ::battle-spectate-change
                                      :is-me (= (:username i) username)
                                      :is-bot (-> i :user :client-status :bot)
                                      :id i}
                :disable (not (or (and am-host (:mode (:battle-status i)))
                                  (= (:username i) username)
                                  (= (:owner i) username)))}})}}
          {:fx/type :table-column
           :text "Faction"
           :cell-value-factory identity
           :cell-factory
           {:fx/cell-type :table-cell
            :describe
            (fn [i]
              {:text ""
               :graphic
               {:fx/type :choice-box
                :value (str (:side (:battle-status i)))
                :on-value-changed {:event/type ::battle-side-change
                                   :is-me (= (:username i) username)
                                   :is-bot (-> i :user :client-status :bot)
                                   :id i}
                :items ["0" "1"]
                :disable (not (or am-host
                                  (= (:username i) username)
                                  (= (:owner i) username)))}})}}
          {:fx/type :table-column
           :text "Rank"
           :cell-value-factory identity
           :cell-factory
           {:fx/cell-type :table-cell
            :describe (fn [i] {:text (str (:rank (:client-status (:user i))))})}}
          {:fx/type :table-column
           :text "TrueSkill"
           :cell-value-factory identity
           :cell-factory
           {:fx/cell-type :table-cell
            :describe (fn [_i] {:text ""})}}
          {:fx/type :table-column
           :text "Color"
           :cell-value-factory identity
           :cell-factory
           {:fx/cell-type :table-cell
            :describe
            (fn [{:keys [team-color] :as i}]
              {:text ""
               :graphic
               {:fx/type :color-picker
                :value (format "#%06x" (fix-color (if team-color (Integer/parseInt team-color) 0)))
                :on-action {:event/type ::battle-color-action
                            :is-me (= (:username i) username)
                            :is-bot (-> i :user :client-status :bot)
                            :id i}
                :disable (not (or am-host
                                  (= (:username i) username)
                                  (= (:owner i) username)))}})}}
          {:fx/type :table-column
           :text "Team"
           :cell-value-factory identity
           :cell-factory
           {:fx/cell-type :table-cell
            :describe
            (fn [i]
              {:text ""
               :graphic
               {:fx/type :choice-box
                :value (str (:id (:battle-status i)))
                :on-value-changed {:event/type ::battle-team-change
                                   :is-me (= (:username i) username)
                                   :is-bot (-> i :user :client-status :bot)
                                   :id i}
                :items (map str (take 16 (iterate inc 0)))
                :disable (not (or am-host
                                  (= (:username i) username)
                                  (= (:owner i) username)))}})}}
          {:fx/type :table-column
           :text "Ally"
           :cell-value-factory identity
           :cell-factory
           {:fx/cell-type :table-cell
            :describe
            (fn [i]
              {:text ""
               :graphic
               {:fx/type :choice-box
                :value (str (:ally (:battle-status i)))
                :on-value-changed {:event/type ::battle-ally-change
                                   :is-me (= (:username i) username)
                                   :is-bot (-> i :user :client-status :bot)
                                   :bot (-> i :user :client-status :bot)
                                   :id i}
                :items (map str (take 16 (iterate inc 0)))
                :disable (not (or am-host
                                  (= (:username i) username)
                                  (= (:owner i) username)))}})}}
          {:fx/type :table-column
           :text "Bonus"
           :cell-value-factory identity
           :cell-factory
           {:fx/cell-type :table-cell
            :describe
            (fn [i]
              {:text ""
               :graphic
               {:fx/type :text-field
                :disable (not am-host)
                :text-formatter
                {:fx/type :text-formatter
                 :value-converter :integer
                 :value (int (or (:handicap (:battle-status i)) 0))
                 :on-value-changed {:event/type ::battle-handicap-change
                                    :id i}}}})}}]}] ; TODO update bot
       (when battle
         [(merge
            {:fx/type battle-buttons
             :am-host am-host
             :battle-map battle-map
             :host-user host-user
             :map-details map-details}
            (select-keys state [:battle :username :host-username :maps-cached
                                :bot-username :bot-name :bot-version :engine-version]))]))}))


(defmethod event-handler ::battle-startpostype-change
  [{:fx/keys [event]}]
  (let [startpostype (get spring/startpostypes-by-name event)]
    (swap! *state assoc-in [:battle :scripttags :startpostype] startpostype)
    (client/send-message (:client @*state) (str "SETSCRIPTTAGS game/startpostype=" startpostype))))

(defmethod event-handler ::battle-ready-change
  [{:fx/keys [event] :keys [battle-status team-color] :as id}]
  (update-battle-status @*state {:id id} (assoc battle-status :ready event) team-color))


(defmethod event-handler ::battle-spectate-change
  [{:keys [id is-me is-bot] :fx/keys [event] :as data}]
  (if (or is-me is-bot)
    (update-battle-status @*state data
      (assoc (:battle-status id) :mode (not event))
      (:team-color id))
    (client/send-message (:client @*state)
      (str "FORCESPECTATORMODE " (:username id)))))

(defmethod event-handler ::battle-side-change
  [{:keys [id] :fx/keys [event] :as data}]
  (when-let [side (try (Integer/parseInt event) (catch Exception _e))]
    (update-battle-status @*state data (assoc (:battle-status id) :side side) (:team-color id))))

(defmethod event-handler ::battle-team-change
  [{:keys [id] :fx/keys [event] :as data}]
  (when-let [player-id (try (Integer/parseInt event) (catch Exception _e))]
    (update-team id data player-id)))

(defmethod event-handler ::battle-ally-change
  [{:keys [id] :fx/keys [event] :as data}]
  (when-let [ally (try (Integer/parseInt event) (catch Exception _e))]
    (update-ally id data ally)))

(defmethod event-handler ::battle-handicap-change
  [{:keys [id] :fx/keys [event]}]
  (when-let [handicap (max 0
                        (min 100
                          event))]
    (client/send-message (:client @*state)
      (str "HANDICAP "
           (:username id)
           " "
           handicap))))

(defmethod event-handler ::battle-color-action
  [{:keys [id] :fx/keys [event] :as opts}]
  (let [javafx-color (.getValue (.getSource event))
        color-int (spring-color javafx-color)]
    (update-color id opts color-int)))


(defn root-view
  [{{:keys [client client-deferred users battles battle battle-password selected-battle username
            password login-error title engine-version mod-name map-name last-failed-message maps-cached
            bot-username bot-name bot-version]} :state}]
  {:fx/type fx/ext-on-instance-lifecycle
   :on-created (fn [_node]
                 (log/debug "on-created")
                 (when-not (:maps-cached @*state)
                   (future
                     (swap! *state assoc :maps-cached (doall (fs/maps))))))
   :on-advanced (fn [_]
                  (log/debug "on-advanced"))
   :on-deleted (fn [_]
                 (log/debug "on-deleted"))
   :desc
   {:fx/type :stage
    :showing true
    :title "Alt Spring Lobby"
    :width 2000
    :height 1200
    :scene {:fx/type :scene
            :stylesheets [(str (io/resource "dark-theme2.css"))]
            :root {:fx/type :v-box
                   :alignment :top-left
                   :children [{:fx/type menu-view}
                              {:fx/type client-buttons
                               :client client
                               :client-deferred client-deferred
                               :username username
                               :password password
                               :login-error login-error}
                              {:fx/type user-table
                               :users users}
                              {:fx/type battles-table
                               :battles battles
                               :users users}
                              {:fx/type battles-buttons
                               :battle battle
                               :battle-password battle-password
                               :battles battles
                               :client client
                               :selected-battle selected-battle
                               :title title
                               :engine-version engine-version
                               :mod-name mod-name
                               :map-name map-name
                               :maps-cached maps-cached}
                              {:fx/type battle-table
                               :battles battles
                               :battle battle
                               :users users
                               :username username
                               :engine-version engine-version
                               :bot-username bot-username
                               :bot-name bot-name
                               :bot-version bot-version
                               :maps-cached maps-cached}
                              {:fx/type :v-box
                               :alignment :center-left
                               :children
                               [{:fx/type :label
                                 :text (str last-failed-message)
                                 :style {:-fx-text-fill "#FF0000"}}]}]}}}})
