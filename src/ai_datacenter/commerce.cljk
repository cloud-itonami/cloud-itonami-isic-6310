(ns ai-datacenter.commerce
  (:require [ai-datacenter.compute :as compute]
            [ai-datacenter.schema :as schema]))

(defn usage-lines
  "Create integer minor-unit lines. Rate denominator avoids floating point."
  [readings {:keys [currency rate-minor-units rate-per-gpu-milliseconds]}]
  (mapv (fn [usage]
          {:line/type :gpu-usage
           :tenant/id (:tenant/id usage) :asset/id (:asset/id usage)
           :line/currency currency
           :line/quantity-gpu-milliseconds (:usage/gpu-milliseconds usage)
           :line/rate-minor-units rate-minor-units
           :line/rate-denominator rate-per-gpu-milliseconds
           :line/amount-minor-units
           (quot (* (:usage/gpu-milliseconds usage) rate-minor-units)
                 rate-per-gpu-milliseconds)
           :line/evidence (:usage/reading-ids usage)})
        (compute/aggregate-usage readings)))

(defn invoice-total [lines]
  (let [currencies (set (map :line/currency lines))]
    (when-not (= 1 (count currencies))
      (throw (ex-info "invoice lines must use one currency" {:currencies currencies})))
    {:currency (first currencies)
     :subtotal-minor-units (reduce + (map :line/amount-minor-units lines))}))

(defn create-invoice [{:keys [invoice-id tenant-id lines]}]
  (let [{:keys [currency subtotal-minor-units]} (invoice-total lines)]
    {:invoice/id invoice-id :tenant/id tenant-id :invoice/currency currency
     :invoice/lines lines :invoice/subtotal-minor-units subtotal-minor-units
     :invoice/status :draft}))

(defn settlement
  [{:keys [settlement-id invoice collected-minor-units
           electricity-minor-units facility-minor-units maintenance-minor-units
           sales-fee-minor-units owner-share-ppm]}]
  (when-not (:valid? (schema/validate-entity :invoice invoice))
    (throw (ex-info "invalid invoice" {})))
  (let [costs (reduce + (map #(or % 0)
                             [electricity-minor-units facility-minor-units
                              maintenance-minor-units sales-fee-minor-units]))
        distributable (- collected-minor-units costs)
        owner-amount (quot (* (max 0 distributable) owner-share-ppm) 1000000)]
    {:settlement/id settlement-id
     :invoice/id (:invoice/id invoice)
     :settlement/currency (:invoice/currency invoice)
     :settlement/collected-minor-units collected-minor-units
     :settlement/cost-minor-units costs
     :settlement/distributable-minor-units distributable
     :settlement/owner-amount-minor-units owner-amount
     :settlement/status :draft}))
