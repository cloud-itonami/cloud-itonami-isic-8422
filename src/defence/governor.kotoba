(ns defence.governor
  "DefenceProcurementGovernor — the independent safety/traceability layer
  for the ISIC Rev.5 8422 defence procurement and logistics administration
  actor. Wired as its own `:govern` node in `defence.actor`'s StateGraph,
  downstream of `:advise` — the Advisor has no notion of vendor provenance
  or budget risk, so this MUST be a separate system able to reject a
  proposal (itonami actor pattern, per ADR-2607011000 / CLAUDE.md Actors
  section).

  `check` is a pure function of (request, context, proposal, store) ->
  verdict; it never mutates the store. The StateGraph's `:decide` node
  routes on the verdict:
    :hard? true                → :hold  (irreversible, no write)
    :escalate? true            → :request-approval (interrupt-before)
    otherwise                  → :commit

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. vendor provenance       — the request's vendor must be registered.
    2. no-actuation            — proposal :effect must be :propose.
    3. scope-boundary          — proposals touching weapons, personnel
                                  deployment, classified operations, or
                                  lethal decisions NEVER PROCEED (closed
                                  allowlist enforced here + in advisor).

  ESCALATION invariants (:escalate? true, ALWAYS human sign-off):
    4. budget-commitment       — procurement commitment above configured
                                  threshold.
    5. contract-award          — contract finalization/award.
    6. low confidence          (< `confidence-floor`)."
  (:require [defence.store :as store]
            [defence.advisor :as advisor]))

(def confidence-floor 0.6)

; Permanently forbidden operation categories
(def ^:private forbidden-ops #{:unknown})  ; :unknown catches out-of-scope proposals from advisor

; Escalating operations (require human approval)
(def ^:private escalating-ops #{:draft-procurement-request
                                 :contract-award})

; Budget threshold for escalation (in currency units)
(def budget-escalation-threshold 1000000)

(defn- hard-violations [{:keys [proposal]} vendor-record]
  (cond-> []
    (nil? vendor-record)
    (conj {:rule :no-vendor :detail "vendor not registered"})

    (not= :propose (:effect proposal))
    (conj {:rule :no-actuation :detail "effect must be :propose only (no direct store writes)"})

    (contains? forbidden-ops (:op proposal))
    (conj {:rule :scope-boundary
           :detail "operation outside permitted scope (weapons, personnel, classified operations, lethal decisions are permanently forbidden)"})))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `defence.store/Store`. Returns
  `{:ok? bool :violations [...] :confidence n :hard? bool :escalate? bool}`."
  [request context proposal store]
  (let [vendor-record (store/vendor store (:vendor-id request))
        hard (hard-violations {:proposal proposal} vendor-record)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        escalating-op? (contains? escalating-ops (:op proposal))
        budget (or (:budget proposal) 0)
        budget-escalate? (> budget budget-escalation-threshold)]
    {:ok? (and (not hard?) (not low?) (not escalating-op?) (not budget-escalate?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? escalating-op? budget-escalate?))}))
