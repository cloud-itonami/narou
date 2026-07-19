(ns lg-narou.test-audit-cron
  "Tests for the audit shim payload + cron spec loading / fire-input shaping."
  (:require [clojure.test :refer [deftest is]]
            [lg-narou.audit :as audit]
            [lg-narou.cron :as cron]))

;; ── audit ─────────────────────────────────────────────────────────────────

(deftest audit-build-payload
  (let [p (audit/build-payload {:actor "did:web:narou.etzhayyim.com"
                                :activity "narou.health.check"
                                :object-id "health:1"
                                :object-type "narou.health"
                                :attributes {:ok true}})]
    ;; camelCase wire shape identical to audit.py
    (is (= "narou.health.check" (:activity p)))
    (is (= "health:1" (:objectId p)))
    (is (= "narou.health" (:objectType p)))
    (is (= {:ok true} (:attributes p)))))

(deftest audit-build-payload-default-attributes
  (is (= {} (:attributes (audit/build-payload {:actor "a" :activity "x"
                                               :object-id "i" :object-type "t"})))))

(deftest audit-http-and-secret-are-explicit-capabilities
  (let [wire (atom nil)]
    (binding [audit/*config* {:url "http://audit.internal" :secret "purpose-bound"
                              :timeout-ms 1000 :disabled? false}
              audit/*http-post* (fn [url opts] (reset! wire [url opts]) {:status 200})]
      (is (= :ok (audit/emit-audit! {:actor "a" :activity "x" :object-id "i"})))
      (is (= "purpose-bound" (get-in @wire [1 :headers "x-internal-trust"]))))))

;; ── cron ──────────────────────────────────────────────────────────────────

(deftest cron-load-specs-filters
  (let [cfg {"crons" [{"schedule" "*/30 * * * *" "graph" "health"}
                      {"schedule" "* * * * *"}          ; no graph → dropped
                      {"graph" "agent_chat"}            ; no schedule → dropped
                      "not-a-map"]}]
    (is (= 1 (count (cron/load-cron-specs cfg))))))

(deftest cron-empty-crons
  (is (= [] (cron/load-cron-specs {"crons" []}))))

(deftest cron-fire-input-passthrough
  (is (= {"novel_id" "n1"}
         (cron/build-fire-input {"novel_id" "n1"} 0))))

(deftest cron-fire-input-rotate-by-epoch
  ;; epoch 1800 → bucket 1 → idx 1 → [1 2]; the flag is consumed (removed)
  (let [out (cron/build-fire-input {"_rotateSceneByEpoch" true} 1800)]
    (is (= [1 2] (get out "scene_indices")))
    (is (not (contains? out "_rotateSceneByEpoch"))))
  ;; epoch 0 → bucket 0 → idx 0 → [0 1]
  (is (= [0 1] (get (cron/build-fire-input {"_rotateSceneByEpoch" true} 0) "scene_indices")))
  ;; epoch wraps: bucket 9 → idx 4 → [4 0]
  (is (= [4 0] (get (cron/build-fire-input {"_rotateSceneByEpoch" true} (* 9 1800)) "scene_indices"))))

(deftest cron-thread-id
  (is (= "cron:health:1800" (cron/fire-thread-id "health" 1800))))
