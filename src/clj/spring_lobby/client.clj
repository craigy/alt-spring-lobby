(ns spring-lobby.client
  (:require
    [aleph.tcp :as tcp]
    [clojure.core.async :as async]
    [gloss.core :as gloss]
    [gloss.io :as gio]
    [manifold.deferred :as d]
    [manifold.stream :as s]
    [taoensso.timbre :refer [info]])
  (:import
    (java.net InetAddress InetSocketAddress)
    (java.security MessageDigest)
    (java.util Base64)))


(def agent-string "alt-spring-lobby-0.1")
(def agent-string)

(def protocol
  (gloss/string :utf-8 :delimeters ["\n"]))


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
  [host port]
  (d/chain (tcp/client {:host host
                        :port port})
    #(wrap-duplex-stream protocol %)))



(defn ping-loop [c]
  (async/thread
    (info "ping loop thread started")
    (loop []
      (async/<!! (async/timeout 30000))
      (info "PING")
      (if @(s/put! c "PING \n")
        (recur)
        (info "ping loop ended")))))

(defn print-loop [c]
  (async/thread
    (info "print loop thread started")
    (loop []
      (if-let [d (s/take! c)]
        (when-let [r @d]
          (info "receive:" (str "'" r "'"))
          (recur))
        (info "print loop ended")))))

(defn exit [c]
  (info "sending EXIT message")
  @(s/put! c "EXIT\n"))

(defn login [c local-addr username password]
  (let [pw-md5-base64 (base64-encode (md5-bytes password))
        git-ref "b6e84c6023cbffac"
        user-id (rand-int Integer/MAX_VALUE)
        compat-flags "sp u"
        msg (str "LOGIN " username " " pw-md5-base64 " 0 " local-addr
                 " " agent-string "\t" user-id " " git-ref "\t" compat-flags "\n")]
    (info "sending LOGIN message")
    @(s/put! c msg)))


(defn connect []
  (let [address "192.168.1.6"
        c @(client address 8200)]
    ;(print-loop c)
    (login c address "skynet9001" "1234dogs")
    (ping-loop c)
    c))

(defn disconnect [c]
  (info "disconnecting")
  (exit c)
  (.close c)
  (info "connection closed?" (.isClosed c)))

(defn message [c m]
  @(s/put! c m))

(defn receive [c]
  @(s/take! c))

#_
(def c (connect))
#_
(message c "LISTCOMPFLAGS\n")
#_
(message c "CHANNELS\n")
#_
(message c "GETCHANNELMESSAGES\n")
#_
(message c "GETUSERINFO skynet9001\n")
#_
(message c "IGNORELIST skynet9001\n")
#_
(receive c)
#_
(disconnect c)
#_
(def p (print-loop c))
#_
(async/close! p)
