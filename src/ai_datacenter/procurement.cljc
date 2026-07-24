(ns ai-datacenter.procurement
  "Vendor registry, RFQ creation and quote comparison. No network effects.")

(def required-vendor-keys
  #{:vendor/id :vendor/legal-name :vendor/role :vendor/source-url
    :vendor/verified-on :vendor/source-status})

(def required-rfq-keys
  #{:rfq/id :buyer/legal-name :delivery/location :workload/profile
    :capacity/gpu-count :power/max-kw :response/due-on})

(defn validate-vendor [vendor]
  (let [missing (remove #(contains? vendor %) required-vendor-keys)]
    {:valid? (empty? missing) :missing (vec missing)}))

(defn vendor-qualification-gaps
  "Public discovery is not procurement qualification. These facts must be
  verified against the entity that will actually quote and contract."
  [vendor]
  (cond-> []
    (not= :verified (:vendor/contracting-entity-status vendor))
    (conj :contracting-entity)
    (not= :verified (:vendor/sales-authority-status vendor))
    (conj :sales-authority)
    (not= :verified (:vendor/payment-destination-status vendor))
    (conj :payment-destination)
    (not= :verified (:vendor/warranty-territory-status vendor))
    (conj :warranty-territory)))

(defn vendors-for
  "Discovery filter only. Region means publicly indicated/seeded coverage,
  not verified sales authority. Omit role to return every role."
  [vendors {:keys [region role]}]
  (->> vendors
       (filter #(or (nil? region) (contains? (:vendor/regions %) region)))
       (filter #(or (nil? role) (contains? (:vendor/role %) role)))
       (sort-by :vendor/id)
       vec))

(defn create-rfq
  "Create a draft only. Sending and accepting a quote are external effects."
  [request]
  (let [missing (remove #(contains? request %) required-rfq-keys)]
    (if (seq missing)
      {:status :invalid :missing (vec missing)}
      {:status :draft
       :approval-required? true
       :request (assoc request
                       :commercial/required
                       [:currency :tax :hardware-price :freight :installation
                        :support :warranty :lead-time :payment-terms]
                       :technical/required
                       [:gpu-model :gpu-memory :cpu :ram :storage :network
                        :rack-units :power-peak-kw :cooling :certifications])})))

(defn create-inquiry
  "Build a vendor-addressed consultation draft. It deliberately has no send
  implementation; an operator must approve and use the vendor's current
  official contact channel."
  [vendor rfq]
  (cond
    (not (:valid? (validate-vendor vendor)))
    {:status :invalid :reason :invalid-vendor}

    (not= :draft (:status rfq))
    {:status :invalid :reason :invalid-rfq}

    (empty? (:vendor/contact-url vendor))
    {:status :hold :reason :contact-channel-unverified}

    :else
    {:status :draft
     :approval-required? true
     :send-blockers (vendor-qualification-gaps vendor)
     :vendor-id (:vendor/id vendor)
     :contact-url (:vendor/contact-url vendor)
     :subject (str "AI計算基盤 見積・構成相談 " (get-in rfq [:request :rfq/id]))
     :body {:request (get rfq :request)
            :questions [:recommended-configuration :availability :lead-time
                        :warranty-support :installation :commercial-terms]}}))

(defn comparable-quote?
  [quote]
  (every? #(contains? quote %)
          [:quote/vendor-id :quote/currency :quote/total-ex-tax
           :quote/valid-until :quote/lead-time-days :quote/warranty-months
           :quote/power-peak-kw :quote/spec-conforms?]))

(defn total-cost
  "Comparable acquisition cost. Unknown components make the result nil."
  [quote]
  (let [parts (map quote [:quote/hardware :quote/freight :quote/installation
                          :quote/support :quote/other])]
    (when (every? number? parts) (reduce + parts))))

(defn rank-quotes
  "Rank only conforming, same-currency complete quotes. Returns a hold otherwise."
  [quotes]
  (let [currencies (set (map :quote/currency quotes))]
    (cond
      (not-every? comparable-quote? quotes)
      {:status :hold :reason :incomplete-quotes}

      (not= 1 (count currencies))
      {:status :hold :reason :currency-normalisation-required}

      :else
      {:status :comparable
       :quotes (->> quotes
                    (filter :quote/spec-conforms?)
                    (map #(assoc % :quote/calculated-total (total-cost %)))
                    (sort-by (juxt :quote/calculated-total :quote/lead-time-days))
                    vec)})))

(def external-effects
  #{:send-rfq :accept-quote :issue-purchase-order :sign-contract :pay-deposit})

(defn govern-action [action]
  (if (contains? external-effects action)
    {:disposition :request-approval :action action}
    {:disposition :commit :action action}))

(defn govern-vendor-action [action vendor]
  (let [gaps (vendor-qualification-gaps vendor)]
    (if (and (contains? external-effects action) (seq gaps))
      {:disposition :hold :action action :reason :vendor-not-qualified
       :missing gaps}
      (govern-action action))))
