(ns probe
  (:require [langgraph.graph :as g]
            [talent.store :as store]
            [talent.operation :as op]
            [clojure.pprint :as pp]))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)
        hrbp {:actor-id "e-100" :actor-role :hrbp :purpose :review :consent? true :phase 3}
        r1 (g/run* actor {:request {:op :survey/analyze :subject "e-002"} :context hrbp}
                   {:thread-id "t1"})]
    (println "== top-level keys ==" (keys r1))
    (println "== status ==" (:status r1))
    (println "== TOP-LEVEL :audit (the nil trap) ==" (pr-str (:audit r1)))
    (println "== state keys ==" (keys (:state r1)))
    (println "== state audit ==")
    (pp/pprint (get-in r1 [:state :audit]))
    (let [r2 (g/run* actor {:approval {:status :approved :by "e-100"}}
                     {:thread-id "t1" :resume? true})]
      (println "\n== resumed status ==" (:status r2))
      (println "== resumed state audit ==")
      (pp/pprint (get-in r2 [:state :audit]))
      (println "== resumed record ==" (pr-str (get-in r2 [:state :record])))
      (println "\n== store insight-of e-002 ==" (pr-str (store/insight-of db "e-002")))
      (println "== ledger ==")
      (pp/pprint (store/ledger db))
      (println "== events key sample ==" (pr-str (take 2 (:events r1)))))))
