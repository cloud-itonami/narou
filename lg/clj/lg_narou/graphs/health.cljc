(ns lg-narou.graphs.health
  "narou `health` graph — simplest end-to-end probe (port of graphs/health.py).

  Replaces BPMN `narou_health` (NSID com.etzhayyim.narou.health). Confirms the
  server can (1) compile a graph, (2) reach the checkpoint store (RW), (3) emit
  audit (fire-and-forget). Primary smoke endpoint for the deploy runbook.

  Topology (faithful): check-rw → summarize → emit-audit → END.

  DEVIATION (noted in PR): the python node ran psycopg `SELECT 1`. There is no
  Postgres driver under babashka, and the charter deprecates RisingWave/Postgres
  in favour of the kotoba Datom log, so the RW liveness check is approximated as
  a TCP-reachability probe to host:port parsed from the conn URL (still proves
  the store is reachable; it does not execute SQL). A `:rw-probe` fn can be
  injected via state to override (used in tests). The python per-node
  RetryPolicy(max_attempts=2) has no langgraph-clj add-node equivalent and is
  dropped (the probe is already best-effort + fail-soft)."
  (:require [clojure.string :as str]
            [langgraph.graph :as g]
            [lg-narou.audit :as audit])
  #?(:clj (:import [java.net Socket InetSocketAddress])))

(def ^:dynamic *config* {:store-url nil :app-did "did:web:narou.etzhayyim.com"})
(defn rw-url [] (:store-url *config*))
(defn default-app-did [] (:app-did *config*))

(defn- now-iso []
  #?(:clj (.format (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm:ss'Z'")
                   (java.time.ZonedDateTime/now (java.time.ZoneOffset/UTC)))
     :default ""))

(defn parse-host-port
  "Extract [host port] from a postgresql:// conn URL (best-effort)."
  [url]
  (when url
    (let [m (re-find #"://(?:[^@/]*@)?([^:/?]+)(?::(\d+))?" url)]
      (when m [(nth m 1) (Integer/parseInt (or (nth m 2) "5432"))]))))

(defn tcp-probe
  "Default RW probe: TCP-connect to host:port (≈ liveness). Returns
  {:rw-ok bool :rw-latency-ms n} or {:rw-ok false :error s}."
  [url]
  #?(:clj
     (if-let [[host port] (parse-host-port url)]
       (let [started (System/nanoTime)]
         (try
           (with-open [sock (Socket.)]
             (.connect sock (InetSocketAddress. ^String host (int port)) 5000))
           {:rw-ok true :rw-latency-ms (long (/ (- (System/nanoTime) started) 1000000))}
           (catch Exception e {:rw-ok false :error (str "rw: " (.getMessage e))})))
       {:rw-ok false :error "rw: unparseable conn URL"})
     :default {:rw-ok false :error "rw: no probe on this host"}))

(defn check-rw [state]
  (let [url (or (:rw-url state) (rw-url))
        probe (get state :rw-probe tcp-probe)]
    (if-not url
      {:rw-ok false :error "RW_URL not set"}
      (let [{:keys [rw-ok rw-latency-ms error]} (probe url)]
        (cond-> {:rw-ok (boolean rw-ok)}
          rw-latency-ms (assoc :rw-latency-ms rw-latency-ms)
          error (assoc :error error))))))

(defn summarize [state]
  {:ok (boolean (:rw-ok state)) :server-now (now-iso)})

(defn emit-audit [state]
  (audit/emit-audit-bg
   {:actor (default-app-did)
    :activity "narou.health.check"
    :object-id (str "health:" #?(:clj (quot (System/currentTimeMillis) 1000) :default 0))
    :object-type "narou.health"
    :attributes {:ok (boolean (:ok state))
                 :rwOk (boolean (:rw-ok state))
                 :rwLatencyMs (or (:rw-latency-ms state) 0)}})
  {})

(defn build []
  (-> (g/state-graph)
      (g/add-node :check-rw check-rw)
      (g/add-node :summarize summarize)
      (g/add-node :emit-audit emit-audit)
      (g/set-entry-point :check-rw)
      (g/add-edge :check-rw :summarize)
      (g/add-edge :summarize :emit-audit)
      (g/set-finish-point :emit-audit)
      (g/compile-graph)))

(def GRAPH (build))
