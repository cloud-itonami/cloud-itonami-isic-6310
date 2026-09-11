(ns ai-datacenter.compute
  (:require [ai-datacenter.schema :as schema]))

(defn valid-reading?
  [reading]
  (and (:valid? (schema/validate-entity :meter-reading reading))
       (integer? (:reading/gpu-milliseconds reading))
       (not (neg? (:reading/gpu-milliseconds reading)))
       (pos? (compare (:reading/to reading) (:reading/from reading)))
       (string? (:reading/source-digest reading))
       (schema/sha256-digest? (:reading/source-digest reading))))

(defn duplicate-reading? [existing reading]
  (boolean (some #(or (= (:reading/id %) (:reading/id reading))
                      (and (= (:reading/source-id %) (:reading/source-id reading))
                           (= (:reading/source-digest %) (:reading/source-digest reading))))
                 existing)))

(defn aggregate-usage [readings]
  (when-not (every? valid-reading? readings)
    (throw (ex-info "invalid meter reading" {})))
  (->> readings
       (group-by (juxt :tenant/id :asset/id))
       (map (fn [[[tenant-id asset-id] rs]]
              {:tenant/id tenant-id :asset/id asset-id
               :usage/gpu-milliseconds (reduce + (map :reading/gpu-milliseconds rs))
               :usage/reading-ids (mapv :reading/id rs)}))
       (sort-by (juxt :tenant/id :asset/id)) vec))

(defn availability
  [{:keys [period-milliseconds excluded-milliseconds outage-milliseconds]}]
  (let [eligible (- period-milliseconds (or excluded-milliseconds 0))
        up (- eligible (or outage-milliseconds 0))]
    (when (pos? eligible)
      {:eligible-milliseconds eligible
       :available-milliseconds up
       :availability-ppm (quot (* up 1000000) eligible)})))
