(ns wasm.achievement-band-test
  "Hosts wasm/achievement_band.wasm (compiled from wasm/achievement_band.kotoba,
  see wasm/README.md) via kototama.tender -- proves talent.hrllm/draft-evaluation's
  MBO/OKR achievement-rate + threshold-band formula runs as a real WASM guest,
  not just as JVM Clojure.

  ABI: main is 0-arity (kotoba wasm emit rejects a parameterized main --
  :main-arity); the two real i32 inputs are written into the guest's
  exported linear memory at fixed offsets before calling main() -- see
  wasm/achievement_band.kotoba's header comment for the offset layout."
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [kototama.contract :as contract]
            [kototama.tender :as tender]))

(defn- wasm-bytes []
  (.readAllBytes (io/input-stream (io/file "wasm/achievement_band.wasm"))))

(defn- run-achievement-band [sum-actual sum-target]
  (let [instance (tender/instantiate (wasm-bytes) [] (contract/host-caps {}))
        memory (.memory instance)]
    (.writeI32 memory 0 sum-actual)
    (.writeI32 memory 4 sum-target)
    (tender/call-main instance)))

(deftest achievement-band-wasm-full-attainment
  (testing "sum-actual >= sum-target (rate >= 100%) -> 達成 (2)"
    ;; hand-verified: 120/100 = 1.20 -> 120% -> >= 100% -> band 2
    (is (= 2 (run-achievement-band 120 100)))))

(deftest achievement-band-wasm-partial-attainment
  (testing "70% <= rate < 100% -> 概ね達成 (1)"
    ;; hand-verified: 75/100 = 0.75 -> 75% -> band 1
    (is (= 1 (run-achievement-band 75 100)))))

(deftest achievement-band-wasm-below-floor
  (testing "rate < 70% -> 未達 (0)"
    ;; hand-verified: 40/100 = 0.40 -> 40% -> band 0
    (is (= 0 (run-achievement-band 40 100)))))

(deftest achievement-band-wasm-no-goals
  (testing "sum-target = 0 (no goals, e.g. a manager with no MBO/OKR goals of\n            their own) -> 未達 (0), not a crash -- matches\n            talent.hrllm/draft-evaluation's own `(if (seq gs) ... 0.0)` fallback"
    (is (= 0 (run-achievement-band 0 0)))))

(deftest achievement-band-wasm-boundaries
  (testing "exact 70% and exact 100% boundaries are inclusive, per the\n            original `(cond (>= rate 1.0) ... (>= rate 0.7) ...)`"
    (is (= 1 (run-achievement-band 70 100)))
    (is (= 2 (run-achievement-band 100 100)))))

(deftest achievement-band-wasm-demo-org-parity
  (testing "e-001's demo goals (g-1: target 12 actual 14, g-2: target 1\n            actual 1) aggregate to sum-actual=15 sum-target=13 -> rate\n            ~115% -> 達成 (2), same band talent.hrllm/draft-evaluation\n            derives from its own mean-of-ratios (1.1667 + 1.0)/2 = 1.0833\n            for this employee -- see src/talent/store.cljc's demo-data"
    (is (= 2 (run-achievement-band 15 13))))
  (testing "e-002's demo goal (g-3: target 8 actual 5) -> rate 62.5% ->\n            未達 (0), same band the original per-goal formula gives\n            (single goal, so mean-of-ratios == ratio-of-sums exactly)"
    (is (= 0 (run-achievement-band 5 8)))))
