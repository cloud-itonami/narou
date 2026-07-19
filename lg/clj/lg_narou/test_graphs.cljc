(ns lg-narou.test-graphs
  "Node-behavior tests for the ported health + agent_chat StateGraphs."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [langgraph.graph :as g]
            [lg-narou.graphs.health :as health]
            [lg-narou.graphs.agent-chat :as chat]))

;; ── health ────────────────────────────────────────────────────────────────

(deftest health-no-rw-url
  ;; no RW url + no injected probe → rw-ok false, "RW_URL not set"
  (let [s (health/check-rw {:rw-url nil})]
    (is (false? (:rw-ok s)))
    (is (= "RW_URL not set" (:error s)))))

(deftest health-injected-probe
  (let [s (health/check-rw {:rw-url "postgresql://u@db.example:4566/x"
                            :rw-probe (fn [_] {:rw-ok true :rw-latency-ms 7})})]
    (is (true? (:rw-ok s)))
    (is (= 7 (:rw-latency-ms s)))))

(deftest health-summarize
  (is (true? (:ok (health/summarize {:rw-ok true}))))
  (is (false? (:ok (health/summarize {:rw-ok false})))))

(deftest health-parse-host-port
  (is (= ["db.example" 4566] (health/parse-host-port "postgresql://u:p@db.example:4566/x")))
  (is (= ["h" 5432] (health/parse-host-port "postgresql://h/x"))))

(deftest health-graph-runs-offline
  ;; full graph invoke with an injected probe (no network, audit disabled by env)
  (let [g* (-> (g/state-graph)
               (g/add-node :check-rw health/check-rw)
               (g/add-node :summarize health/summarize)
               (g/set-entry-point :check-rw)
               (g/add-edge :check-rw :summarize)
               (g/set-finish-point :summarize)
               (g/compile-graph))
        out (g/invoke g* {:rw-url "postgresql://u@h:4566/x"
                          :rw-probe (fn [_] {:rw-ok true :rw-latency-ms 3})})]
    (is (true? (:ok out)))
    (is (true? (:rw-ok out)))))

;; ── agent_chat ──────────────────────────────────────────────────────────────

(deftest chat-resolve-actor
  (is (= "did:web:narou.etzhayyim.com:actor:writer"
         (:actor-did (chat/resolve-actor {}))))
  (is (= "editor" (:actor-role (chat/resolve-actor {:actor-role "editor"})))))

(deftest chat-system-prompt-fallback
  (is (= (chat/system-prompt "writer") (chat/system-prompt "bogus-role"))))

(deftest chat-build-messages
  (let [msgs (chat/build-messages {:actor-role "editor" :novel-id "n1"
                                   :message "tighten this"
                                   :history [{:role "user" :content "hi"}
                                             {:role "assistant" :content "hello"}
                                             {:role "system" :content "drop me"}]})]
    (is (= "system" (:role (first msgs))))
    (is (str/includes? (:content (first msgs)) "novel_id=n1"))
    (is (= "tighten this" (:content (last msgs))))
    ;; system + 2 valid history (system role dropped) + user = 4
    (is (= 4 (count msgs)))))

(deftest chat-llm-empty-message
  (is (= "message required" (:error (chat/llm-call {:message "  "})))))

(deftest chat-llm-injected-post-ok
  (let [fake (fn [_url _payload _t]
               {:model "tier0-general"
                :choices [{:message {:content "  drafted prose  "}}]
                :usage {:prompt_tokens 10 :completion_tokens 20 :total_tokens 30}})
        out (chat/llm-call {:message "write" :llm-post fake})]
    (is (= "drafted prose" (:reply out)))
    (is (= 30 (:total-tokens out)))
    (is (= "tier0-general" (:model out)))))

(deftest chat-llm-injected-post-http-error
  (let [fake (fn [_u _p _t] {:lg-narou.graphs.agent-chat/http-error 500
                             :lg-narou.graphs.agent-chat/text "boom"})
        out (chat/llm-call {:message "write" :llm-post fake})]
    (is (str/starts-with? (:error out) "vllm http 500"))))
