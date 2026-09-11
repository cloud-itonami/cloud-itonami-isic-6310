(ns ai-datacenter.evidence
  (:require [ai-datacenter.schema :as schema]))

(defn valid?
  [evidence now]
  (and (:valid? (schema/validate-entity :evidence evidence))
       (schema/sha256-digest? (:evidence/digest evidence))
       (or (nil? (:evidence/expires-at evidence))
           (pos? (compare (:evidence/expires-at evidence) now)))
       (not= :revoked (:evidence/status evidence))))

(defn covers?
  [evidence evidence-type subject now]
  (and (valid? evidence now)
       (= evidence-type (:evidence/type evidence))
       (= subject (:evidence/subject evidence))))

(defn missing-types [evidence required subject now]
  (->> required
       (remove (fn [t] (some #(covers? % t subject now) evidence)))
       sort vec))

(defn valid-approval?
  [approval proposal-digest allowed-roles]
  (and (:valid? (schema/validate-entity :approval approval))
       (= :approved (:approval/status approval))
       (= proposal-digest (:approval/proposal-digest approval))
       (contains? allowed-roles (:approval/role approval))
       (schema/sha256-digest? proposal-digest)))
