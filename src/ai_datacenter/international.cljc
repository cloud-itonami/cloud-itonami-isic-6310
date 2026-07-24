(ns ai-datacenter.international
  "Jurisdiction and cross-border gates for the GPU supply chain.")

(def supported-jurisdictions #{:JPN :USA :CHN :HKG :EU})

(def common-evidence
  #{:vendor-legal-identity :beneficial-owner-screening :sanctions-screening
    :export-classification :end-user-end-use :importer-of-record
    :customs-tax-assessment :title-risk-transfer :cargo-insurance
    :facility-power-cooling-approval :electrical-fire-approval
    :data-classification :security-assessment :incident-contacts
    :warranty-service-territory :disposal-recycling-plan})

(def jurisdiction-evidence
  {:JPN #{:jpn-tax-adviser-memo :jpn-data-transfer-review}
   :USA #{:usa-ear-review :usa-ofac-screening :usa-state-privacy-review
          :usa-state-facility-permits}
   :CHN #{:chn-export-import-review :chn-cybersecurity-mlps-review
          :chn-pipl-data-localisation-transfer-review :chn-icp-review}
   :HKG #{:hkg-import-export-review :hkg-pdpo-transfer-review
          :hkg-facility-approvals}
   :EU #{:eu-member-state :eu-gdpr-transfer-review :eu-nis2-scope-review
         :eu-energy-reporting-review :eu-ce-rohs-weee-review
         :eu-member-state-tax-permits}})

(defn required-evidence
  [jurisdiction]
  (into common-evidence (get jurisdiction-evidence jurisdiction #{})))

(defn assess-market-entry
  "Evidence is a set of independently issued/approved evidence keys.
  EU requires a Member State because permits and tax are not EU-uniform."
  [{:keys [jurisdiction evidence eu-member-state]}]
  (cond
    (not (contains? supported-jurisdictions jurisdiction))
    {:disposition :hold :reason :unsupported-jurisdiction}

    (and (= :EU jurisdiction) (empty? eu-member-state))
    {:disposition :hold :reason :eu-member-state-required}

    :else
    (let [missing (vec (sort (remove (or evidence #{})
                                    (required-evidence jurisdiction))))]
      (if (seq missing)
        {:disposition :hold :reason :missing-jurisdiction-evidence
         :jurisdiction jurisdiction :missing missing}
        {:disposition :request-approval :reason :market-entry-review-complete
         :jurisdiction jurisdiction}))))

(defn assess-route
  "Cross-border hardware routes require both origin/export and destination/
  import packs. A domestic route still requires the destination pack."
  [{:keys [origin destination packs]}]
  (let [needed (set [origin destination])
        missing (vec (sort (remove #(= :approved (get-in packs [% :status])) needed)))]
    (if (seq missing)
      {:disposition :hold :reason :unapproved-route-jurisdiction :missing missing}
      {:disposition :request-approval :reason :route-ready})))
