(ns spring-lobby.client
  (:require
    [aleph.tcp :as tcp]
    [byte-streams]
    [clojure.core.async :as async]
    [clojure.java.io :as io]
    [clojure.string :as string]
    [gloss.core :as gloss]
    [gloss.io :as gio]
    [manifold.deferred :as d]
    [manifold.stream :as s]
    [org.clojars.smee.binary.core :as b]
    [taoensso.timbre :refer [info trace warn]])
  (:import
    (java.net InetAddress InetSocketAddress)
    (java.nio ByteBuffer)
    (java.security MessageDigest)
    (java.util Base64)))

(def agent-string "alt-spring-lobby-0.1")

(def default-address "192.168.1.6")
(def default-port 8200)

; https://springrts.com/dl/LobbyProtocol/ProtocolDescription.html

(def protocol
  (gloss/compile-frame
    (gloss/delimited-frame
      ["\n"]
      (gloss/string :utf-8))
    str
    str)) ; TODO parse here

(def client-status-protocol
  (gloss/compile-frame
    (gloss/bit-map :prefix 1 :bot 1 :access 1 :rank 3 :away 1 :ingame 1)))

(def battle-status-protocol
  (gloss/compile-frame
    (gloss/bit-map :prefix 6 :side 2 :sync 2 :pad 4 :handicap 7 :mode 1 :ally 4 :id 4 :ready 1 :suffix 1)))

(def default-client-status "0")

(defn decode-client-status [status-str]
  (dissoc
    (gio/decode client-status-protocol
      (byte-streams/convert
        (.array
          (.put
            (ByteBuffer/allocate 1)
            (Byte/parseByte status-str)))
        ByteBuffer))
    :prefix))

(defn decode-battle-status [status-str]
  (dissoc
    (gio/decode battle-status-protocol
      (byte-streams/convert
        (.array
          (.putInt
            (ByteBuffer/allocate (quot Integer/SIZE Byte/SIZE))
            (Integer/parseInt status-str)))
        ByteBuffer))
    :prefix :pad :suffix))


; https://stackoverflow.com/a/39188819/984393
(defn base64-encode [bs]
  (.encodeToString (Base64/getEncoder) bs))

; https://gist.github.com/jizhang/4325757
(defn md5-bytes [s]
  (let [algorithm (MessageDigest/getInstance "MD5")]
    (.digest algorithm (.getBytes s))))


; https://aleph.io/examples/literate.html#aleph.examples.tcp

(defn wrap-duplex-stream
  [protocol s]
  (let [out (s/stream)]
    (s/connect
      (s/map #(gio/encode protocol %) out)
      s)
    (s/splice
      out
      (gio/decode-stream s protocol))))

(defn client
  ([]
   (client default-address default-port))
  ([host port]
   (d/chain (tcp/client {:host host
                         :port port})
     #(wrap-duplex-stream protocol %))))


(defn send-message [c m]
  (info ">" (str "'" m "'"))
  @(s/put! c (str m "\n")))

(defmulti handle
  (fn [c state m]
    (-> m
        (string/split #"\s")
        first)))

(defmethod handle :default [c state m]
  (trace "no handler for message" (str "'" m "'")))

(defn parse-adduser [m]
  (re-find #"\w+ (\w+) ([^\s]+) (\w+) (.*)" m))

(defmethod handle "ADDUSER" [c state m]
  (let [[all username country id user-agent] (parse-adduser m)
        user {:username username
              :country country
              :user-id id
              :user-agent user-agent
              :client-status (decode-client-status default-client-status)}]
    (swap! state assoc-in [:users username] user)))

(defmethod handle "REMOVEUSER" [c state m]
  (let [[all username] (re-find #"\w+ (\w+)" m)]
    (swap! state update :users dissoc username)))


(defn parse-battleopened [m]
  (re-find #"[^\s]+ ([^\s]+) ([^\s]+) ([^\s]+) ([^\s]+) ([^\s]+) ([^\s]+) ([^\s]+) ([^\s]+) ([^\s]+) ([^\s]+) ([^\s]+)\t([^\t]+)\t([^\t]+)\t([^\t]+)\t([^\t]+)\t([^\t]+)" m))

(defmethod handle "BATTLEOPENED" [c state m]
  (if-let [[all battle-id battle-type battle-nat-type host-username battle-ip battle-port battle-maxplayers battle-passworded battle-rank battle-maphash battle-engine battle-version battle-map battle-title battle-modname channel-name] (parse-battleopened m)]
    (let [battle {:battle-id battle-id
                  :battle-type battle-type
                  :battle-nat-type battle-nat-type
                  :host-username host-username
                  :battle-ip battle-ip
                  :battle-port battle-port
                  :battle-maxplayers battle-maxplayers
                  :battle-passworded battle-passworded
                  :battle-rank battle-rank
                  :battle-maphash battle-maphash
                  :battle-engine battle-engine
                  :battle-version battle-version
                  :battle-map battle-map
                  :battle-title battle-title
                  :battle-modname battle-modname
                  :channel-name channel-name}]
      (swap! state assoc-in [:battles battle-id] battle))
    (warn "Unable to parse BATTLEOPENED")))

(defn parse-updatebattleinfo [m]
  (re-find #"[^\s]+ ([^\s]+) ([^\s]+) ([^\s]+) ([^\s]+) (.+)" m))

(defmethod handle "UPDATEBATTLEINFO" [c state m]
  (let [[all battle-id battle-spectators battle-locked battle-maphash battle-map] (parse-updatebattleinfo m)
        battle {:battle-id battle-id
                :battle-spectators battle-spectators
                :battle-locked battle-locked
                :battle-maphash battle-maphash
                :battle-map battle-map}]
    (swap! state update-in [:battles battle-id] merge battle)))

(defmethod handle "BATTLECLOSED" [c state m]
  (let [[all battle-id] (re-find #"\w+ (\w+)" m)]
    (swap! state
      (fn [state]
        (let [battle-name (-> state :battles (get battle-id) :battle-name)
              curr-battle-name (-> state :battle :battle-name)
              state (update state :battles dissoc battle-id)]
          (if (= battle-name curr-battle-name)
            (dissoc state :battle)
            state))))))

(defmethod handle "CLIENTSTATUS" [c state m]
  (let [[all username client-status] (re-find #"\w+ (\w+) (\w+)" m)]
    (swap! state assoc-in [:users username :client-status] (decode-client-status client-status))))

(defmethod handle "JOIN" [c state m]
  (let [[all channel-name] (re-find #"\w+ (\w+)" m)]
    (swap! state assoc-in [:my-channels channel-name] {})))

(defmethod handle "JOINED" [c state m]
  (let [[all channel-name username] (re-find #"\w+ (\w+) (\w+)" m)]
    (swap! state assoc-in [:channels channel-name :users username] {})))

(defn parse-joinbattle [m]
  (re-find #"\w+ ([^\s]+) ([^\s]+) ([^\s]+)" m))

(defmethod handle "JOINBATTLE" [c state m]
  (let [[all battle-id hash-code channel-name] (parse-joinbattle m)]
    (swap! state assoc :battle {:battle-id battle-id
                                :hash-code hash-code
                                :channel-name channel-name})))

(defmethod handle "JOINEDBATTLE" [c state m]
  (let [[all battle-id username] (re-find #"\w+ (\w+) (\w+)" m)]
    (swap! state
      (fn [state]
        (let [initial-status {}
              state (assoc-in state[:battles battle-id :users username] initial-status)]
          (if (= battle-id (-> state :battle :battle-id))
            (assoc-in state [:battle :users username] initial-status)
            state))))))

(defmethod handle "LEFT" [c state m]
  (let [[all channel-name username] (re-find #"\w+ (\w+) (\w+)" m)]
    (swap! state update-in [:channels :users] dissoc username)))

(defmethod handle "LEFTBATTLE" [c state m]
  (let [[all battle-id username] (re-find #"\w+ (\w+) (\w+)" m)]
    (swap! state
      (fn [state]
        (-> state
            (update-in [:battle :users] dissoc username)
            (update-in [:battles battle-id :users] dissoc username))))))

(defmethod handle "REQUESTBATTLESTATUS" [c state m]
  (send-message c "MYBATTLESTATUS 0 0")) ; TODO real status

(defmethod handle "CLIENTBATTLESTATUS" [c state m]
  (let [[all username battle-status team-color] (re-find #"\w+ (\w+) (\w+) (\w+)" m)]
    (swap! state update-in [:battle :users username] merge {:battle-status (decode-battle-status battle-status)
                                                            :team-color team-color})))

(defn ping-loop [c]
  (async/thread
    (info "ping loop thread started")
    (loop []
      (async/<!! (async/timeout 30000))
      (if (send-message c "PING")
        (recur)
        (info "ping loop ended")))))

(defn print-loop [c state]
  (async/thread
    (info "print loop thread started")
    (loop []
      (if-let [d (s/take! c)]
        (if-let [m @d]
          (do
            (info "<" (str "'" m "'"))
            (handle c state m)
            (recur))
          (info "print loop ended"))
        (info "print loop ended")))))

(defn exit [c]
  (send-message c "EXIT"))

(defn login
  [client local-addr username password]
  (let [pw-md5-base64 (base64-encode (md5-bytes password))
        git-ref "b6e84c6023cbffac"
        user-id (rand-int Integer/MAX_VALUE)
        compat-flags "sp u"
        msg (str "LOGIN " username " " pw-md5-base64 " 0 " local-addr
                 " " agent-string "\t" user-id " " git-ref "\t" compat-flags)]
    (send-message client msg)))


(defn connect
  ([state]
   (connect state (client)))
  ([state client]
   (print-loop client state)
   (login client default-address "skynet9001" "1234dogs")
   (ping-loop client)))

(defn disconnect [c]
  (info "disconnecting")
  (exit c)
  (.close c)
  (info "connection closed?" (.isClosed c)))

(defn open-battle [c {:keys [battle-type nat-type battle-password host-port max-players mod-hash rank map-hash
                             engine engine-version map-name title mod-name]
                      :or {battle-type 0
                           nat-type 0
                           battle-password "*"
                           host-port 8452
                           max-players 8
                           rank 0
                           engine "Spring"}
                      :as opts}]
  (send-message c
    (str "OPENBATTLE " battle-type " " nat-type " " battle-password " " host-port " " max-players
         " " mod-hash " " rank " " map-hash " " engine "\t" engine-version "\t" map-name "\t" title
         "\t" mod-name)))
