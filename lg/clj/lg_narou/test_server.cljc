(ns lg-narou.test-server
  "clojure.test port of tests/test_smoke.py (server / NSID / langgraph.json parity)."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [cheshire.core :as json]
            [langchain.runnable :as r]
            [langgraph.graph :as g]
            [lg-narou.server :as srv]))

(def expected-graphs #{"health" "agent_chat"})

(def expected-nsid-map
  {"com.etzhayyim.narou.health"    "health"
   "com.etzhayyim.narou.chat"      "agent_chat"
   "com.etzhayyim.narou.agentChat" "agent_chat"})

(deftest graphs-match-expected-set
  (is (= expected-graphs (set (keys srv/GRAPHS)))))

(deftest nsid-map-completeness
  (is (= expected-nsid-map srv/NSID->ASSISTANT)))

(deftest nsid-map-references-known-graphs
  (doseq [[_ gname] srv/NSID->ASSISTANT]
    (is (contains? srv/GRAPHS gname))))

(deftest all-graphs-are-invocable
  (doseq [[_ graph] srv/GRAPHS]
    (is (satisfies? r/IRunnable graph))))

(def lg-dir
  (let [f (java.io.File. ^String (str *file*))]
    ;; clj/lg_narou/test_server.cljc → lg/ is two parents up from lg_narou/
    (.getParentFile (.getParentFile (.getParentFile (.getAbsoluteFile f))))))

(deftest langgraph-json-graphs-match-server
  (let [cfg (json/parse-string (slurp (java.io.File. lg-dir "langgraph.json")))
        declared (set (keys (get cfg "graphs")))]
    (is (= declared (set (keys srv/GRAPHS))))))

(deftest langgraph-json-has-no-crons
  (let [cfg (json/parse-string (slurp (java.io.File. lg-dir "langgraph.json")))]
    (is (= [] (get cfg "crons" [])))))

;; ── handler routing (no socket) ──────────────────────────────────────────

(deftest ok-endpoint-lists-graphs
  (let [r (srv/handler {:request-method :get :uri "/ok"})]
    (is (= 200 (:status r)))
    (let [body (json/parse-string (:body r) true)]
      (is (true? (:ok body)))
      (is (= expected-graphs (set (:graphs body)))))))

(deftest health-endpoint
  (let [r (srv/handler {:request-method :get :uri "/health"})]
    (is (= 200 (:status r)))
    (is (true? (:ok (json/parse-string (:body r) true))))))

(deftest unknown-nsid-xrpc-404
  (let [r (srv/handler {:request-method :post
                        :uri "/xrpc/com.etzhayyim.narou.unknownMethod"
                        :body "{}"})]
    (is (= 404 (:status r)))))

(deftest runs-unknown-graph-404
  (let [r (srv/handler {:request-method :post :uri "/runs"
                        :body (json/generate-string {:assistant_id "nope"})})]
    (is (= 404 (:status r)))))

(deftest key-normalization
  (is (= {:actor-role "writer" :novel-id "n1"}
         (srv/normalize-input {"actorRole" "writer" "novel_id" "n1"}))))

(deftest server-start-requires-explicit-capability
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"explicit server capability"
                        (srv/start! nil {:port 0}))))
