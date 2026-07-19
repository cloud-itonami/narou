(ns lg-narou.server
  "OSS HTTP server for lg-narou (port of `lg_narou/server.py`, FastAPI → httpkit).

  Same minimal HTTP surface:
    POST /runs                 → invoke a graph synchronously
    POST /runs/stream          → graph superstep events as SSE
    POST /xrpc/{nsid}          → XRPC-compat shim (NSID → assistant_id)
    GET  /threads/{tid}/state  → latest checkpoint snapshot
    GET  /ok | /health         → liveness / readiness

  Auth: optional `LG_API_KEY` enforces `x-api-key` on /runs paths. /xrpc/{nsid}
  is unauthenticated (trust at the cloudflared tunnel layer, like the python).

  The request routing is a pure ring-style `handler` (testable without a socket);
  `-main`/`start!` boot org.httpkit.server (bb built-in).

  DEVIATIONS (noted in PR):
   - FastAPI/uvicorn → org.httpkit.server; pydantic body → cheshire parse.
   - The RW _RwAsyncPostgresSaver checkpointer (checkpointer.py) is RW/Postgres-
     specific and NOT ported (charter deprecates RisingWave); graphs compile
     without a checkpointer, so /threads/{tid}/state returns an empty snapshot
     unless a checkpointer is later wired (kotoba/datomic-isomorphic, ADR-2605312345).
   - /runs/stream computes events then flushes them as SSE (not incrementally
     streamed). JSON output keys are kebab (clj state) rather than snake."
  (:require [clojure.string :as str]
            [langgraph.graph :as g]
            [lg-narou.graphs.health :as health]
            [lg-narou.graphs.agent-chat :as agent-chat]
            [lg-narou.cron :as cron]
            #?(:clj [cheshire.core :as json])))

(def ^:dynamic *api-key* "")

(def GRAPHS {"health" health/GRAPH "agent_chat" agent-chat/GRAPH})

(def NSID->ASSISTANT
  {"com.etzhayyim.narou.health"    "health"
   "com.etzhayyim.narou.chat"      "agent_chat"
   "com.etzhayyim.narou.agentChat" "agent_chat"})

(defn api-key [] (str/trim *api-key*))

;; ── key normalization (camelCase | snake_case → kebab keyword) ──────────

(defn camel->snake [s]
  (->> s
       (map-indexed (fn [i ch]
                      (if (and (Character/isUpperCase ^char ch) (pos? i))
                        (str "_" (Character/toLowerCase ^char ch))
                        (str (Character/toLowerCase ^char ch)))))
       (apply str)))

(defn key->kw [k]
  (-> (name k) camel->snake (str/replace "_" "-") keyword))

(defn normalize-input [m]
  (into {} (map (fn [[k v]] [(key->kw k) v]) m)))

;; ── JSON-safe serialization of graph state ──────────────────────────────

(defn safe-json [v]
  (cond
    (map? v) (into {} (map (fn [[k x]] [k (safe-json x)]) v))
    (sequential? v) (mapv safe-json v)
    (or (string? v) (number? v) (boolean? v) (nil? v)) v
    :else (let [s (pr-str v)] (subs s 0 (min 200 (count s))))))

;; ── responses ───────────────────────────────────────────────────────────

(defn- json-resp [status body]
  {:status status
   :headers {"Content-Type" "application/json"}
   :body #?(:clj (json/generate-string body) :default body)})

(defn- parse-body [req]
  #?(:clj
     (let [b (:body req)
           s (cond (nil? b) "" (string? b) b :else (slurp b))]
       (if (str/blank? s) {} (json/parse-string s)))
     :default (or (:parsed-body req) {})))

(defn- now-ms [] #?(:clj (System/currentTimeMillis) :default 0))
(defn- elapsed [t0] (- (now-ms) t0))

(defn- require-api-key [req]
  (let [k (api-key)]
    (or (str/blank? k)
        (= k (get-in req [:headers "x-api-key"])))))

;; ── route handlers ───────────────────────────────────────────────────────

(defn- handle-ok [_]
  (json-resp 200 {:ok true :graphs (vec (keys GRAPHS)) :version "0.1.0"}))

(defn- handle-health [_]
  ;; readiness mirrors python's checkpointer-presence check; we have no RW
  ;; checkpointer wired, so liveness == graphs compiled.
  (json-resp 200 {:ok true :checkpointer false :graphs (vec (keys GRAPHS))}))

(defn- handle-runs [req]
  (if-not (require-api-key req)
    (json-resp 401 {:detail "invalid x-api-key"})
    (let [body (parse-body req)
          assistant (str (or (get body "assistant_id") (get body :assistant_id) ""))]
      (if-not (contains? GRAPHS assistant)
        (json-resp 404 {:detail (str "unknown graph: " assistant)})
        (let [graph (GRAPHS assistant)
              input (normalize-input (or (get body "input") (get body :input) {}))
              t0 (now-ms)]
          (try
            (let [result (g/invoke graph input)]
              (json-resp 200 {:ok true :result (safe-json result)
                              :assistantId assistant :latencyMs (elapsed t0)}))
            (catch #?(:clj Exception :default :default) e
              (json-resp 200 {:ok false
                              :error (let [m (str #?(:clj (.getMessage e) :default e))]
                                       (subs m 0 (min 500 (count m))))
                              :errorType #?(:clj (.. e getClass getSimpleName) :default "err")
                              :assistantId assistant :latencyMs (elapsed t0)}))))))))

(defn- handle-stream [req]
  (if-not (require-api-key req)
    (json-resp 401 {:detail "invalid x-api-key"})
    (let [body (parse-body req)
          assistant (str (or (get body "assistant_id") (get body :assistant_id) ""))]
      (if-not (contains? GRAPHS assistant)
        (json-resp 404 {:detail (str "unknown graph: " assistant)})
        (let [graph (GRAPHS assistant)
              input (normalize-input (or (get body "input") (get body :input) {}))
              sse (try
                    (->> (g/stream graph input)
                         (map (fn [ev]
                                (str "data: "
                                     #?(:clj (json/generate-string {:event "values" :data (safe-json (:state ev))})
                                        :default (pr-str (safe-json (:state ev))))
                                     "\n\n")))
                         (apply str))
                    (catch #?(:clj Exception :default :default) e
                      (str "data: "
                           #?(:clj (json/generate-string {:event "error" :data (str #?(:clj (.getMessage e) :default e))})
                              :default (str e))
                           "\n\n")))]
          {:status 200 :headers {"Content-Type" "text/event-stream"} :body sse})))))

(defn- handle-xrpc [nsid req]
  (let [assistant (NSID->ASSISTANT nsid)]
    (cond
      (nil? assistant) (json-resp 404 {:detail (str "unknown NSID: " nsid)})
      (not (contains? GRAPHS assistant)) (json-resp 503 {:detail (str "graph not loaded: " assistant)})
      :else
      (let [graph (GRAPHS assistant)
            body (parse-body req)
            input (normalize-input (if (map? body) body {}))
            t0 (now-ms)]
        (try
          (let [result (g/invoke graph input)
                out (if (map? result) (safe-json result) {:result (safe-json result)})]
            (json-resp 200 (assoc out :latencyMs (elapsed t0) :assistantId assistant)))
          (catch #?(:clj Exception :default :default) e
            (json-resp 200 {:error (str "lg-narou " #?(:clj (.. e getClass getSimpleName) :default "err"))
                            :errorDetail (let [m (str #?(:clj (.getMessage e) :default e))]
                                           (subs m 0 (min 300 (count m))))
                            :assistantId assistant :latencyMs (elapsed t0)})))))))

(defn- handle-thread-state [tid req]
  (if-not (require-api-key req)
    (json-resp 401 {:detail "invalid x-api-key"})
    (let [assistant (get-in req [:query-params "assistant_id"])]
      (if-not (contains? GRAPHS assistant)
        (json-resp 404 {:detail (str "unknown graph: " assistant)})
        (let [graph (GRAPHS assistant)
              snap (try (g/get-state graph tid) (catch #?(:clj Exception :default :default) _ nil))]
          (json-resp 200 {:values (safe-json (or (:state snap) {}))
                          :next (vec (or (:frontier snap) []))
                          :tasks []}))))))

;; ── router ────────────────────────────────────────────────────────────────

(defn handler
  "Pure ring-style router. req: {:request-method :uri :headers :body :query-params}."
  [req]
  (let [m (:request-method req)
        uri (:uri req)]
    (cond
      (and (= m :get) (= uri "/ok")) (handle-ok req)
      (and (= m :get) (= uri "/health")) (handle-health req)
      (and (= m :post) (= uri "/runs")) (handle-runs req)
      (and (= m :post) (= uri "/runs/stream")) (handle-stream req)
      (and (= m :post) (str/starts-with? uri "/xrpc/"))
      (handle-xrpc (subs uri (count "/xrpc/")) req)
      (and (= m :get) (str/starts-with? uri "/threads/") (str/ends-with? uri "/state"))
      (handle-thread-state (-> uri (subs (count "/threads/")) (str/replace #"/state$" "")) req)
      :else (json-resp 404 {:detail "not found"}))))

#?(:clj
   (defn start!
     "Boot the httpkit server. opts: {:port n}. Returns the stop fn."
     [run-server & [{:keys [port] :or {port 8080}}]]
     (when-not (fn? run-server)
       (throw (ex-info "Narou requires explicit server capability" {})))
     ;; cron is loaded for parity (narou ships zero crons → nil)
     (let [crons (cron/start-cron GRAPHS)]
       (println (str "lg-narou server up: graphs=" (vec (keys GRAPHS))
                     " crons=" (boolean crons) " port=" port))
       (run-server handler {:port port}))))

#?(:clj
   (defn -main [& _]
     (throw (ex-info "Narou portable runtime requires a host adapter" {}))))
