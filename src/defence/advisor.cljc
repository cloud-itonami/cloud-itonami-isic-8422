(ns defence.advisor
  "DefenceProcurementAdvisor — proposes a procurement or logistics
  operation (register vendor, draft procurement request, schedule
  equipment maintenance, coordinate logistics) for a registered vendor.
  The advisor is swappable: `mock-advisor` (deterministic, default in
  dev/tests/CI) or `llm-advisor` (wraps a real `langchain.model/
  ChatModel`). Either way the advisor ONLY produces a PROPOSAL — it never
  writes to the store and has no notion of vendor provenance or budget
  risk; `defence.governor` is the independent system that decides
  whether the proposal may proceed, per the itonami actor pattern.

  A proposal is a map:
    {:op :register-vendor|:draft-procurement|:schedule-maintenance|:coordinate-logistics
     :effect :propose        ; the advisor NEVER emits a raw store write
     :stake :low|:medium|:high
     :confidence 0.0-1.0
     :rationale str}

  CLOSED ALLOWLIST: the advisor's proposal vocabulary is restricted to
  administrative operations ONLY. Proposals touching weapons, personnel
  deployment, classified operations, or lethal decisions are structurally
  impossible — the advisor cannot construct them.

  LLM parse failures always yield `:confidence 0.0` (never fabricate
  confidence), which forces the governor to escalate/hold."
  (:require [clojure.string :as str]))

; Closed allowlist: only these operations are permitted
(def permitted-ops #{:register-vendor
                      :draft-procurement-request
                      :schedule-equipment-maintenance
                      :coordinate-logistics})

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- infer
  "Deterministic mock inference: reads the request's declared op/stake
  straight through (a stand-in for what an LLM would extract from free
  text), with a stake-derived confidence. Enforces closed allowlist:
  only permitted-ops are allowed."
  [_store {:keys [op stake budget] :as request}]
  (if (contains? permitted-ops op)
    (cond-> {:op op
             :effect :propose
             :stake (or stake :low)
             :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
             :rationale (str "proposed " (name op) " for vendor " (:vendor-id request))}
      budget (assoc :budget budget))
    {:op :unknown
     :effect :propose
     :stake :high
     :confidence 0.0
     :rationale "operation not in permitted allowlist (scope boundary)"}))

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are a defence procurement advisor. Given a procurement or
   logistics request, propose ONLY ONE of these operations:
   :register-vendor, :draft-procurement-request,
   :schedule-equipment-maintenance, :coordinate-logistics.

   STRICTLY FORBIDDEN: any operation touching weapons, personnel
   deployment, classified information, or lethal decisions. If the
   request implies any of these, respond with :confidence 0.0 and
   :op :unknown.

   Always provide an honest :confidence (0.0-1.0) and a :stake
   (:low/:medium/:high). Never fabricate confidence you don't have.")

(defn- parse-proposal [content]
  (try
    (let [p (read-string content)
          op-valid? (and (map? p) (contains? permitted-ops (:op p)))]
      (if op-valid?
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "operation outside permitted allowlist"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  "Wraps a `langchain.model/ChatModel`. `gen-opts` is passed through to
  `model/-generate`. Kept decoupled from any concrete model so this ns
  has no hard dependency beyond `langchain.model`'s protocol."
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "procurement request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
