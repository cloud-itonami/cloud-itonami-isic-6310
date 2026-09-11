(ns ai-datacenter.supply-chain
  "Pure, auditable procurement-case state machine. External sends remain
  governed effects; this namespace only validates and advances case state.")

(def stages
  [:requirements-approved :rfq-approved :rfq-issued :quotes-received
   :quote-selected :contract-approved :po-approved :ordered])

(def ^:private next-stage
  (zipmap stages (concat (rest stages) [nil])))

(def required-artifacts
  {:requirements-approved #{:requirements}
   :rfq-approved #{:rfq}
   :rfq-issued #{:rfq-send-receipt}
   :quotes-received #{:original-quotes}
   :quote-selected #{:quote-comparison :conflict-disclosure}
   :contract-approved #{:signed-contract :legal-review}
   :po-approved #{:purchase-order :budget-approval :vendor-due-diligence
                  :export-import-review}
   :ordered #{:purchase-order-send-receipt}})

(defn open-case
  [{:keys [case-id engagement-id market buyer-id]}]
  (if (every? some? [case-id engagement-id market buyer-id])
    {:status :ok
     :case {:procurement-case/id case-id
            :engagement/id engagement-id
            :market market
            :buyer/id buyer-id
            :procurement-case/stage nil
            :procurement-case/artifacts {}
            :procurement-case/history []}}
    {:status :invalid :reason :missing-case-identity}))

(defn attach-artifact
  "Attach immutable evidence by digest. Reusing a key with another digest is
  rejected so an original quote or approval cannot be silently replaced."
  [case artifact-type {:keys [digest] :as artifact}]
  (let [existing (get-in case [:procurement-case/artifacts artifact-type])]
    (cond
      (not (and (keyword? artifact-type) (string? digest)))
      {:status :invalid :reason :invalid-artifact}

      (and existing (not= (:digest existing) digest))
      {:status :hold :reason :artifact-conflict :artifact-type artifact-type}

      :else
      {:status :ok
       :case (assoc-in case [:procurement-case/artifacts artifact-type] artifact)})))

(defn advance
  "Advance exactly one stage after checking stage-specific artifacts."
  [case target-stage {:keys [at by]}]
  (let [current (:procurement-case/stage case)
        expected (if (nil? current) (first stages) (next-stage current))
        required (get required-artifacts target-stage)
        available (set (keys (:procurement-case/artifacts case)))
        missing (vec (sort (remove available required)))]
    (cond
      (not= target-stage expected)
      {:status :hold :reason :invalid-stage-transition
       :current current :expected expected :requested target-stage}

      (not (every? some? [at by]))
      {:status :invalid :reason :missing-actor-or-time}

      (seq missing)
      {:status :hold :reason :missing-artifacts :missing missing}

      :else
      {:status :ok
       :case (-> case
                 (assoc :procurement-case/stage target-stage)
                 (update :procurement-case/history conj
                         {:from current :to target-stage :at at :by by}))})))

(defn production-ready?
  [case]
  (= :ordered (:procurement-case/stage case)))
