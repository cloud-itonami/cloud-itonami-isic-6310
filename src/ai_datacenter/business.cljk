(ns ai-datacenter.business
  "Pure business lifecycle for an ISIC 6310 GPU-compute operator.")

(def stages
  [:lead :qualified :tax-review :contracted :procured :installed
   :commissioned :serving :settled :retired])

(def ^:private transitions
  {:qualify          [:lead :qualified]
   :request-tax-review [:qualified :tax-review]
   :sign-contract    [:tax-review :contracted]
   :procure-gpu      [:contracted :procured]
   :record-installation [:procured :installed]
   :commission       [:installed :commissioned]
   :start-serving    [:commissioned :serving]
   :settle-period    [:serving :settled]
   :open-next-period [:settled :serving]
   :retire-asset     [:settled :retired]})

(def externally-certified
  "Claims which itonami records but must never decide by itself."
  #{:tax-eligibility :electrical-compliance :facility-acceptance})

(def human-approval-required
  #{:sign-contract :procure-gpu :commission :retire-asset})

(defn new-engagement
  [{:keys [id customer-id]}]
  {:engagement/id id
   :customer/id customer-id
   :stage :lead
   :assets []
   :periods []
   :evidence []
   :audit []})

(defn propose
  "Return a decision without mutating state. Evidence is append-only input;
  tax eligibility is an adviser/certifier assertion, not an itonami verdict."
  [engagement op evidence]
  (let [[from to] (get transitions op)
        evidence-types (set (map :type evidence))
        missing (case op
                  :sign-contract (when-not (contains? evidence-types :tax-eligibility)
                                   [:tax-eligibility])
                  :commission (seq (remove evidence-types
                                           [:electrical-compliance :facility-acceptance]))
                  nil)]
    (cond
      (nil? from) {:disposition :hold :reason :unknown-operation}
      (not= from (:stage engagement)) {:disposition :hold :reason :invalid-stage}
      (seq missing) {:disposition :hold :reason :missing-evidence :missing (vec missing)}
      (contains? human-approval-required op)
      {:disposition :request-approval :operation op :next-stage to}
      :else {:disposition :commit :operation op :next-stage to})))

(defn commit
  "Apply an approved proposal and append an audit event."
  [engagement proposal evidence]
  (when-not (#{:commit :approved} (:disposition proposal))
    (throw (ex-info "proposal is not committable" {:proposal proposal})))
  (-> engagement
      (assoc :stage (:next-stage proposal))
      (update :evidence into evidence)
      (update :audit conj (select-keys proposal [:operation :next-stage :disposition]))))

(defn period-economics
  "Compute operating economics from measured integer yen values.
  Revenue guarantees and tax savings are deliberately absent."
  [{:keys [compute-revenue-yen electricity-yen facility-yen maintenance-yen fees-yen]}]
  (let [revenue (or compute-revenue-yen 0)
        costs (reduce + (map #(or % 0)
                             [electricity-yen facility-yen maintenance-yen fees-yen]))]
    {:revenue-yen revenue :cost-yen costs :operating-margin-yen (- revenue costs)}))
