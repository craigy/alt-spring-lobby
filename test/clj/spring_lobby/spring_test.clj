(ns spring-lobby.spring-test
  (:require
    [clojure.data]
    [clojure.test :refer [deftest is testing]]
    [spring-lobby.spring :as spring]))


(declare battle battle-players expected-script-data expected-script-data-players
         expected-script-txt expected-script-txt-players)


(deftest script-data
  (testing "no players"
    (is (= expected-script-data
           (assoc-in
             (spring/script-data battle)
             [:game :hostip] nil))))
  (testing "player and bot"
    (is (= expected-script-data-players
           (spring/script-data battle-players)))))

(deftest script-test
  #_
  (is (= expected-script-txt
         (spring/script-txt
           (sort-by first expected-script-data))))
  #_
  (testing "with players"
    (let [expected expected-script-txt-players
          actual (spring/script-txt
                   (sort-by first expected-script-data-players))]
      (is (= expected actual))
      (when (not= expected actual)
        (println (str "expected:\n" expected))
        (println (str "actual:\n" actual))
        (let [diff (clojure.data/diff (string/split-lines expected) (string/split-lines actual))]
          (println "diff:")
          (pprint diff))))))


(deftest parse-script
  #_
  (is (= expected-script-data
         (assoc-in
           (spring/parse-script expected-script-txt)
           [:game :hostip] nil)))
  #_
  (is (= nil
         (spring/parse-script
           (slurp "/mnt/c/Users/craig/Desktop/Dworld Acidic.smd"))))
  #_
  (is (= nil
         (spring/parse-script
           (slurp "/mnt/c/Users/craig/Desktop/Zeus05_A.smd"))))
  #_
  (is (= nil
         (spring/parse-script
           (slurp "/mnt/c/Users/craig/Desktop/Xelric_Draw_beta2.smd"))))
  #_
  (is (= {}
         (spring/parse-script
           (slurp "/mnt/c/Users/craig/Desktop/mars.smd"))))
  #_
  (is (= {}
         (spring/parse-script
           (slurp "/mnt/c/Users/craig/Desktop/CorePrime_revision1.smd"))))
  #_
  (is (= {}
         (spring/parse-script
           (slurp "/mnt/c/Users/craig/Desktop/Calamity_V1.smd"))))
  #_
  (is (= {}
         (spring/parse-script
           (slurp "/mnt/c/Users/craig/Desktop/hourglass.smd")))))


(def battle
  {:battle-modhash -1706632985
   :battle-version "104.0.1-1510-g89bb8e3 maintenance"
   :battle-map "Dworld Acidic"
   :battle-title "deth"
   :battle-modname "Balanced Annihilation V9.79.4"
   :battle-maphash -1611391257
   :battle-port 8452
   :battle-ip "127.0.0.1"})

(def expected-script-data
  {:game
   {:gametype "Balanced Annihilation V9.79.4"
    :mapname "Dworld Acidic"
    :hostport 8452
    :hostip nil
    :ishost 0}})

(def battle-players
  {:battle-modhash -1
   :battle-version "103.0"
   :battle-map "Dworld Duo"
   :battle-title "deth"
   :battle-modname "Balanced Annihilation V10.24"
   :battle-maphash -1
   :battle-port 8452
   :battle-ip nil ;"192.168.1.6"
   :users
   {"skynet9001"
    {:battle-status
     {:id 0
      :ally 0
      :team-color 0
      :handicap 0
      :mode 1
      :side 0}
     :team-color 0}}
   :bots
   {"kekbot1"
    {:ai-name "KAIK"
     :ai-version "0.13"
     :owner "skynet9001"
     :team-color 1
     :battle-status
     {:id 1
      :ally 1
      :team-color 1
      :handicap 1
      :mode 1
      :side 1}}}})

(def expected-script-data-players
  {:game
   {:gametype "Balanced Annihilation V10.24"
    :mapname "Dworld Duo"
    :hostport 8452
    :hostip nil
    :ishost 0
    "team0"
    {:teamleader 0
     :handicap 0
     :allyteam 0
     :rgbcolor "0.0 0.0 0.0"
     :side "ARM"}
    "team1"
    {:teamleader 0
     :handicap 1
     :allyteam 1
     :rgbcolor "0.00392156862745098 0.0 0.0"
     :side "CORE"},
    "allyteam1" {:numallies 0}
    "allyteam0" {:numallies 0}
    "ai1"
    {:name "kekbot1"
     :shortname "KAIK"
     :version "0.13"
     :host 0
     :team 1,
     :isfromdemo 0
     :options {}},
    "player0"
    {:name "skynet9001",
     :team 0,
     :isfromdemo 0,
     :countrycode nil
     :spectator 0}}})



(def expected-script-txt
  "[game]
{
\tgametype = Balanced Annihilation V9.79.4;
\thostip = ;
\thostport = 8452;
\tishost = 1;
\tmapname = Dworld Acidic;
}

")

(def expected-script-txt-players
  "[game]
{
\t[ai1]
\t{
\t\thost = 0;
\t\tisfromdemo = 0;
\t\tname = kekbot1;
\t\t[options]
\t\t{
\t\t}

\t\tshortname = KAIK;
\t\tteam = 1;
\t\tversion = 0.13;
\t}

\t[allyteam0]
\t{
\t}

\t[allyteam1]
\t{
\t}

\tgametype = Balanced Annihilation V10.24;
\thostip = 192.168.1.6;
\thostport = 8452;
\tishost = 1;
\tmapname = Dworld Duo;
\tnumplayers = 1;
\tnumusers = 2;
\t[player0]
\t{
\t\tcountrycode = ;
\t\tisfromdemo = 0;
\t\tname = skynet9001;
\t\tteam = 0;
\t}

\tstartpostype = 2;
\t[team0]
\t{
\t\tallyteam = 0;
\t\thandicap = 0;
\t\trgbcolor = 0 0 0;
\t\tside = 0;
\t\tteamleader = 0;
\t}

\t[team1]
\t{
\t\tallyteam = 1;
\t\thandicap = 1;
\t\trgbcolor = 0 0 1;
\t\tside = 1;
\t\tteamleader = 1;
\t}

}

")
