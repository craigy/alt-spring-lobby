(ns spring-lobby.spring
  "Interface to run Spring."
  (:require
    [clojure.string :as string]))


(defn script-data
  "Given data for a battle, returns data that can be directly formatted to script.txt format for Spring."
  [battle]
  (into
    {:gametype (:battle-modname battle)
     :mapname (:battle-map battle)
     :hostip nil ;(:battle-ip battle)
     :hostport (:battle-port battle)
     :ishost 1 ; TODO
     :numplayers 1 ; TODO
     :startpostype 2 ; TODO
     :numusers (count (:users battle))} ; TODO
    (map-indexed
      (fn [i [player {:keys [battle-status]}]]
        [(str "player" (:id battle-status))
         {:name player
          :team (:ally battle-status)
          :isfromdemo false}]) ; TODO
      (:users battle))))

(defn script-txt-inner
  ([kv]
   (script-txt-inner "\t" kv))
  ([tabs [k v]]
   (str tabs
        (if (map? v)
          (str "[" (name k ) "]\n"
               (apply str (map (partial script-txt-inner (str tabs "\t")) v)))
          (str (name k) " = " v ";"))
        "\n")))

; https://springrts.com/wiki/Script.txt
; https://github.com/spring/spring/blob/104.0/doc/StartScriptFormat.txt
; https://github.com/springlobby/springlobby/blob/master/src/spring.cpp#L284-L590
(defn script-txt
  "Given data for a battle, return contents of a script.txt file for Spring."
  [script-data]
  (str "[game]\n" ; TODO
    (apply str
      (map script-txt-inner script-data))))
