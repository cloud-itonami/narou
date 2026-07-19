#!/usr/bin/env bb
;; lg-narou — bb-native test runner (clojure.test; no shell). ADR-2606280030.
;;
;; Per the repo-wide rule (root CLAUDE.md §"Operational code = clj/bb"): new
;; first-party tooling is clj/bb, NOT shell. This is the .clj runner the repo
;; rule mandates for ported actors/apps (replaces run_tests.sh).
;;
;;   bb run_tests.clj          ; from 60-apps/etzhayyim-project-narou/lg/
;;   bb test                   ; via the scoped bb.edn task
(require '[babashka.http-client :as http]
         '[clojure.test :as t]
         '[org.httpkit.server :as httpkit]
         '[lg-narou.audit :as audit]
         '[lg-narou.cron :as cron]
         '[lg-narou.graphs.agent-chat :as chat]
         '[lg-narou.graphs.health :as health]
         '[lg-narou.server :as server])

(defn- env [name default] (or (System/getenv name) default))
(def app-did (env "NAROU_APP_DID" "did:web:narou.etzhayyim.com"))

(defn handler [request]
  (binding [audit/*config* {:url (env "BPMN_DISPATCHER_INTERNAL_URL" (:url audit/default-config))
                            :secret (env "BPMN_DISPATCHER_INTERNAL_SECRET" "")
                            :timeout-ms (long (* 1000 (Double/parseDouble (env "LG_AUDIT_TIMEOUT_SEC" "3.0"))))
                            :disabled? (= "1" (env "LG_AUDIT_DISABLED" "0"))}
            audit/*http-post* http/post
            cron/*config* {:enabled? (contains? #{"1" "true" "yes"}
                                                 (clojure.string/lower-case (env "LG_CRON_ENABLED" "true")))
                           :langgraph-json (env "LANGGRAPH_JSON" "/app/langgraph.json")}
            chat/*config* {:url (env "VLLM_URL" "http://127.0.0.1:4000/v1")
                           :model (env "VLLM_MODEL" "tier0-general")
                           :timeout-ms (long (* 1000 (Double/parseDouble (env "VLLM_TIMEOUT_SEC" "60"))))
                           :app-did app-did}
            chat/*llm-post* http/post
            health/*config* {:store-url (or (System/getenv "RW_URL")
                                            (System/getenv "LG_CHECKPOINTER_URL"))
                             :app-did app-did}
            server/*api-key* (env "LG_API_KEY" "")]
    (server/handler request)))

(defn start-server! [port]
  (server/start! (fn [_ options] (httpkit/run-server handler options)) {:port port}))

(def suites
  '[lg-narou.test-audit-cron
    lg-narou.test-graphs
    lg-narou.test-server])

(apply require suites)

(let [{:keys [fail error]} (apply t/run-tests suites)]
  (if (zero? (+ fail error))
    (println "── lg-narou: ALL suites green ──")
    (do (println "── lg-narou: FAILURES above ──")
        (System/exit 1))))
