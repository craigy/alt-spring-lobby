(ns spring-lobby.lua
  (:require
    [clj-antlr.core :as antlr])
  (:import
    (org.luaj.vm2 LuaValue)
    (org.luaj.vm2.lib.jse JsePlatform)))


(def lua-parser
  (future (antlr/parser "lib/grammars-v4/lua/Lua.g4")))


(defn parse [s]
  (@lua-parser s))

#_
(parse (slurp "mapinfo.lua"))


(defn table-to-map
  "Returns a map from the given lua table, with keys converted to keywowrds and inner table values
  converted to maps as well."
  [lv]
  (let [table (.checktable lv)]
    (loop [m {}
           prevk LuaValue/NIL]
      (let [kv (.next table prevk)
            k (.arg1 kv)
            v (.arg kv 2)]
        (if (.isnil k)
          m
          (recur
            (let [kk (keyword (.toString k))
                  vs (cond
                       (.islong v) (.tolong v)
                       (.isdouble v) (.todouble v)
                       (.istable v) (table-to-map v)
                       :else
                       (.toString v))]
              (assoc m kk vs))
            k))))))

(defn read-mapinfo
  "Returns a map repsenting mapinfo from the given string representation of mapinfo.lua."
  [s]
  (let [globals (JsePlatform/standardGlobals)
        lua-chunk (.load globals s)
        res (.call lua-chunk)]
    (table-to-map res)))


#_
(let [globals (JsePlatform/standardGlobals)
      lua-chunk (.load globals "local x = 0")]
  (.type lua-chunk)
  (.call lua-chunk)
  (println (.type globals)))
#_
(let [s (-> (io/file "/mnt/c/Users/craig/Desktop/mapinfo.lua")
            slurp)
      globals (JsePlatform/standardGlobals)
      lua-chunk (.load globals s)]
  (println (.typename globals))
  ;(println (str lua-chunk))
  (let [res (.call lua-chunk)]
    (println (.typename res))
    (println res)
    (table-to-map res)))
#_
(let [lv (-> (io/file "/mnt/c/Users/craig/Desktop/mapinfo.lua")
             slurp
             (read-mapinfo))]
  ;(println (.isfunction lv))
  ;(println (.type lv))
  ;(println (.toString lv))
  (println (.type lv))
  (println (.toString lv))
  (println (.get lv (LuaValue/valueOf "mapinfo")))
  (println (.len lv))
  lv)
#_
(do
  (println LuaValue/TBOOLEAN)
  (println LuaValue/TFUNCTION)
  (println LuaValue/TINT)
  (println LuaValue/TLIGHTUSERDATA)
  (println LuaValue/TNIL)
  (println LuaValue/TNONE)
  (println LuaValue/TNUMBER)
  (println LuaValue/TSTRING)
  (println LuaValue/TTABLE)
  (println LuaValue/TTHREAD)
  (println LuaValue/TUSERDATA)
  (println LuaValue/TVALUE))
