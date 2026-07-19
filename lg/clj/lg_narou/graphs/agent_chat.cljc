(ns lg-narou.graphs.agent-chat
  "narou `agent_chat` graph — novel-writer AI persona chat (port of graphs/agent_chat.py).

  Forwards a single user turn to an OpenAI-compatible chat-completions endpoint
  with a writer-role system prompt. Roles map to novel-production roles:
  writer / editor / worldbuilder / character / reader. Replaces text-generation
  Zeebe tasks (createChapter / generateChapter side LLM calls).

  Topology (faithful): resolve-actor → llm-call → emit-audit → END.

  DEVIATIONS (noted in PR):
   - httpx → babashka.http-client; json → cheshire (repo rule, #2612 httpx→bb).
   - Inference endpoint DEFAULT changed from the RunPod proxy to the Murakumo
     loopback (LiteLLM 127.0.0.1:4000) per ADR-2605215000 / the GPU-inference
     substrate row (Murakumo DEFAULT-PREFERRED). `VLLM_URL` still overrides.
   - The python RetryPolicy(max_attempts=3) on the llm node has no langgraph-clj
     add-node equivalent and is dropped (error is returned in state, not raised).
   - The HTTP post fn is injectable via state `:llm-post` for tests (no network)."
  (:require [clojure.string :as str]
            [langgraph.graph :as g]
            [lg-narou.audit :as audit]
            #?(:clj [cheshire.core :as json])))

(def ^:dynamic *config* {:url "http://127.0.0.1:4000/v1" :model "tier0-general"
                         :timeout-ms 60000 :app-did "did:web:narou.etzhayyim.com"})
(def ^:dynamic *llm-post* nil)
(defn vllm-url [] (str/replace (:url *config*) #"/+$" ""))
(defn vllm-model [] (:model *config*))
(defn vllm-timeout-ms [] (long (:timeout-ms *config*)))
(defn default-app-did [] (:app-did *config*))

(def actor-prompts
  {"writer" (str "You are a novelist AI. You draft Japanese-style web novel prose "
                 "(narrative-first, character-driven, light pacing). Reply in the "
                 "user's language. Stay tight (≤500 words per turn unless the user "
                 "asks for a full chapter). Show, don't tell.")
   "editor" (str "You are a novel editor AI. You give structural feedback: pacing, "
                 "POV consistency, scene economy, hooks. Reply with bullet-point "
                 "actionable notes (max 8 bullets). Reference specific lines when "
                 "possible. Be direct, kind, not preachy.")
   "worldbuilder" (str "You are a worldbuilder AI. You design settings, magic systems, "
                       "factions, geography, history. Reply with a structured outline: "
                       "setting → factions → magic → conflict drivers. Cap at 350 words.")
   "character" (str "You are a character designer AI. You build characters with goals, "
                    "wounds, contradictions, voice. Reply with a structured sheet: "
                    "Name / Role / Want / Need / Wound / Voice sample (1 line).")
   "reader" (str "You are a reader AI giving honest reaction (kandō). React in the "
                 "moment — what hit, what confused, what made you pause. Stay short "
                 "(≤120 words). Be specific, not generic.")})

(defn system-prompt [role] (or (get actor-prompts role) (get actor-prompts "writer")))

(defn resolve-actor [state]
  (let [role (or (not-empty (:actor-role state)) "writer")]
    {:actor-did (str (default-app-did) ":actor:" role) :actor-role role}))

(defn build-messages
  "Pure: the OpenAI chat `messages` array for this turn (system + ≤12 history + user)."
  [{:keys [actor-role novel-id chapter-id message history]}]
  (let [role (or (not-empty actor-role) "writer")
        novel (or novel-id "")
        chapter (or chapter-id "")
        system (cond-> (system-prompt role)
                 (or (seq novel) (seq chapter))
                 (str "\n\nContext: novel_id=" (if (seq novel) novel "<none>")
                      ", chapter_id=" (if (seq chapter) chapter "<none>") "."))
        hist (->> (or history [])
                  (take-last 12)
                  (keep (fn [h]
                          (when (map? h)
                            (let [r (or (get h :role) (get h "role"))
                                  c (or (get h :content) (get h "content"))]
                              (when (and (#{"user" "assistant"} r) (string? c) (seq c))
                                {:role r :content (subs c 0 (min 2000 (count c)))}))))))
        user (subs (str/trim (or message "")) 0 (min 4000 (count (str/trim (or message "")))))]
    (vec (concat [{:role "system" :content system}] hist [{:role "user" :content user}]))))

(defn- default-llm-post
  "Real OpenAI-compatible POST → parsed JSON map (or throws)."
  [url payload timeout-ms]
  #?(:clj
     (let [_ (when-not (fn? *llm-post*)
               (throw (ex-info "Narou LLM requires explicit HTTP" {})))
           resp (*llm-post* (str url "/chat/completions")
                           {:headers {"Content-Type" "application/json"}
                            :body (json/generate-string payload)
                            :timeout timeout-ms
                            :throw false})
           status (:status resp)]
       (if (>= status 400)
         {::http-error status ::text (subs (str (:body resp)) 0 (min 200 (count (str (:body resp)))))}
         (json/parse-string (:body resp) true)))
     :default {::http-error 0 ::text "no http on this host"}))

(defn llm-call [state]
  (let [user-text (str/trim (or (:message state) ""))]
    (if (empty? user-text)
      {:error "message required"}
      (let [messages (build-messages state)
            payload {:model (vllm-model)
                     :messages messages
                     :max_tokens (long (or (:max-tokens state) 512))
                     :temperature (double (or (:temperature state) 0.85))}
            post-fn (get state :llm-post default-llm-post)
            started #?(:clj (System/nanoTime) :default 0)
            latency (fn [] #?(:clj (long (/ (- (System/nanoTime) started) 1000000)) :default 0))]
        (try
          (let [resp (post-fn (vllm-url) payload (vllm-timeout-ms))]
            (if-let [s (::http-error resp)]
              {:error (str "vllm http " s ": " (::text resp)) :latency-ms (latency)}
              (let [choice (or (first (:choices resp)) {})
                    msg (or (:message choice) {})
                    usage (or (:usage resp) {})]
                {:reply (str/trim (or (:content msg) ""))
                 :model (or (:model resp) (vllm-model))
                 :prompt-tokens (long (or (:prompt_tokens usage) 0))
                 :completion-tokens (long (or (:completion_tokens usage) 0))
                 :total-tokens (long (or (:total_tokens usage) 0))
                 :latency-ms (latency)})))
          (catch #?(:clj Exception :default :default) e
            {:error (let [m (str "vllm: " #?(:clj (.. e getClass getSimpleName) :default "err")
                                 ": " #?(:clj (.getMessage e) :default (str e)))]
                      (subs m 0 (min 200 (count m))))
             :latency-ms (latency)}))))))

(defn emit-audit [state]
  (audit/emit-audit-bg
   {:actor (or (:actor-did state) (default-app-did))
    :activity "narou.chat.reply"
    :object-id (str "chat:" (or (:actor-role state) "writer") ":"
                    #?(:clj (quot (System/currentTimeMillis) 1000) :default 0))
    :object-type "narou.chat"
    :attributes {:actorRole (:actor-role state)
                 :novelId (:novel-id state)
                 :userDid (:user-did state)
                 :totalTokens (or (:total-tokens state) 0)
                 :latencyMs (or (:latency-ms state) 0)
                 :model (:model state)
                 :ok (not (boolean (:error state)))}})
  {})

(defn build []
  (-> (g/state-graph)
      (g/add-node :resolve-actor resolve-actor)
      (g/add-node :llm-call llm-call)
      (g/add-node :emit-audit emit-audit)
      (g/set-entry-point :resolve-actor)
      (g/add-edge :resolve-actor :llm-call)
      (g/add-edge :llm-call :emit-audit)
      (g/set-finish-point :emit-audit)
      (g/compile-graph)))

(def GRAPH (build))
