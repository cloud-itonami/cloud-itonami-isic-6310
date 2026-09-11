(ns ai-datacenter.schema)

(def entity-required
  {:engagement #{:engagement/id :customer/id :jurisdiction :stage}
   :asset #{:asset/id :asset/owner-id :asset/manufacturer :asset/model
            :asset/serial :asset/status}
   :vendor #{:vendor/id :vendor/legal-name :vendor/role}
   :rfq #{:rfq/id :engagement/id :rfq/status :rfq/specification}
   :quote #{:quote/id :rfq/id :vendor/id :quote/currency
            :quote/valid-until :quote/status}
   :contract #{:contract/id :engagement/id :contract/type
               :contract/parties :contract/status}
   :purchase-order #{:purchase-order/id :engagement/id :rfq/id :quote/id
                     :vendor/id :purchase-order/currency
                     :purchase-order/total-minor-units :purchase-order/status}
   :shipment #{:shipment/id :purchase-order/id :shipment/status
               :shipment/origin :shipment/destination}
   :meter-reading #{:reading/id :asset/id :tenant/id :reading/from
                    :reading/to :reading/gpu-milliseconds :reading/source-id}
   :invoice #{:invoice/id :tenant/id :invoice/currency :invoice/lines
              :invoice/status}
   :evidence #{:evidence/id :evidence/type :evidence/subject
               :evidence/issuer :evidence/issued-at :evidence/digest}
   :approval #{:approval/id :approval/proposal-digest :approval/status
               :approval/by :approval/role :approval/at}})

(defn validate-entity [entity-type entity]
  (let [required (get entity-required entity-type)
        missing (vec (sort (remove #(contains? entity %) required)))]
    {:valid? (and (some? required) (empty? missing))
     :entity-type entity-type
     :missing missing}))

(defn sha256-digest? [s]
  (boolean (and (string? s) (re-matches #"sha256:[0-9a-f]{64}" s))))
