# ADR 0001: Defence Procurement Actor Architecture

## Status
Accepted

## Context

ISIC Rev.5 8422 (Defence Procurement and Logistics Administration) is the administrative and logistical side of defence ministry operations. This is distinct from combat operations, weapons systems, or personnel command authority. The challenge is designing an actor that supports **authentic** procurement and logistics administration while enforcing **absolute scope boundaries** on what operations are permitted.

The threat model:
- **Unintended scope creep**: a well-intentioned actor designed for "procurement" can drift into weapons procurement or personnel deployment if the governance layer is not explicit.
- **LLM drift**: an LLM-backed advisor can hallucinate operations outside its intended scope (e.g., "authorize personnel deployment" or "select targeting parameters").
- **Human override**: even with escalation, a human operator under time pressure might approve a proposal at the boundary.

The solution is a **closed allowlist** of permitted operations, enforced in both the Advisor and Governor layers, with hard invariants that make some proposals structurally impossible to construct.

## Decision

Implement the Defence Procurement Actor as:

### 1. Closed Allowlist (Advisor Layer)

The `defence.advisor` namespace restricts its proposal vocabulary to exactly these operations:
- `:register-vendor` — vendor/contractor verification and registration
- `:draft-procurement-request` — new procurement intake and drafting
- `:schedule-equipment-maintenance` — equipment maintenance scheduling
- `:coordinate-logistics` — non-combat logistics coordination (transport, warehousing, supply chain)

An LLM advisor is instructed to respond with `:op :unknown` and `:confidence 0.0` if the request implies any out-of-scope operation. A mock advisor validates that the op is in the permitted set and rejects it with zero confidence if not. Parsing failures also yield `:confidence 0.0`.

### 2. Hard Invariants (Governor Layer)

The `defence.governor/check` function enforces three classes of hard violations that always route to `:hold` (no approval path):

1. **Vendor provenance**: The vendor must be registered in the store. An unregistered vendor is an immediate, non-overridable block.

2. **No direct actuation**: The proposal's `:effect` must be `:propose` only. Any proposal claiming `:effect :direct-write` or attempting to mutate the store directly is held.

3. **Scope boundary (permanent)**: Any proposal with `:op :unknown` (a catch-all for out-of-scope requests from the advisor) is immediately held. This is the structural gate: the advisor's closed allowlist + zero-confidence response to out-of-scope requests + the governor's rejection of `:unknown` ops create a two-layer enforcement.

Hard violations are **non-overridable**. There is no escalation path, no human approval route, and no threshold above which they are waived. If a proposal violates a hard invariant, it permanently `:hold`s.

### 3. Escalation Invariants (Human Sign-Off)

Operations that are **not** hard violations but carry higher risk require explicit human approval:

1. **Procurement request drafting** (`:op :draft-procurement-request`) — requires human review of the scope, vendor terms, and deliverables.

2. **Budget commitment above threshold** — proposals with `budget > 1000000` (configurable) require human sign-off due to financial exposure.

3. **Low advisor confidence** — proposals with `confidence < 0.6` are escalated regardless of operation type. This forces human judgment when the LLM is uncertain.

Escalation routes to `:request-approval`, an `interrupt-before` node that checkpoints the graph. Resume (human approval) is explicit: `(actor/approve! graph thread-id)` advances to `:commit`. This is the human-in-the-loop boundary: the operator sees the proposal, rationale, and risk assessment and decides whether to proceed.

### 4. Closed Allowlist Rationale

A closed allowlist ("`only` these ops are allowed") is stronger than a denylist ("`never` these ops"). A denylist is fragile: if the advisor design omits a forbidden op from the denylist, the actor can drift into new scope. A closed allowlist forces the actor design to be explicit about every permitted operation, and any new operation requires deliberate design review and testing.

Example: if the architect listed forbidden ops as `#{:weapons :personnel-deployment}`, an LLM advisor trained on general text might propose `:authorize-lethal-strikes` or `:conduct-target-acquisition`, operations that were never explicitly forbidden and thus would slip through. With a closed allowlist, any op outside `#{:register-vendor :draft-procurement-request :schedule-equipment-maintenance :coordinate-logistics}` is structurally rejected at the governance layer.

## Consequences

### Positive

- **No scope drift**: the allowlist is explicit and must be reviewed to expand.
- **LLM safety**: even if an LLM advisor hallucinates an out-of-scope operation, the advisor layer catches it with zero confidence, and the governor holds it.
- **Clear human responsibility**: escalated proposals (human-sign-off ops) are visibly distinct from automatic-commit ops (low-risk vendor registrations, routine logistics).
- **Auditability**: every held proposal leaves a ledger entry. A human audit can review the hard holds and confirm that no weapons/personnel/classified proposals ever reached a human reviewer.

### Negative

- **Strictness**: legitimate new operations require design review and code change. Operators cannot expand scope dynamically via config (this is a feature for safety, not a bug).
- **LLM instructions**: the system prompt and advisor logic must carefully encode the allowlist. If the LLM is updated, the instructions must be updated in parallel.

## Implementation Details

### Store Protocol

```clojure
(defprotocol Store
  (vendor [s vendor-id])           ; retrieve registered vendor
  (records-of [s vendor-id])       ; all procurement/logistics records for a vendor
  (ledger [s])                     ; append-only audit trail
  (register-vendor! [s vendor])    ; register a vendor
  (commit-record! [s record])      ; commit a procurement/logistics record
  (append-ledger! [s fact]))       ; append a ledger entry
```

### Advisor Interface

```clojure
(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

; Proposal:
{:op :register-vendor|:draft-procurement-request|:schedule-equipment-maintenance|:coordinate-logistics
 :effect :propose
 :stake :low|:medium|:high
 :confidence 0.0-1.0
 :rationale "explanation"}

; Out-of-scope:
{:op :unknown
 :effect :propose
 :confidence 0.0
 :stake :high
 :rationale "operation not in permitted allowlist (scope boundary)"}
```

### Governor Verdict

```clojure
{:ok? bool              ; true if hard + escalation checks pass
 :violations [...]      ; list of hard violations (if any)
 :confidence n          ; advisor confidence (for audit)
 :hard? bool            ; true if any hard violation
 :escalate? bool}       ; true if escalation invariant triggered
```

### StateGraph

```
:intake -> :advise -> :govern -> :decide -+-> :commit           (:ok? true)
                                           +-> :request-approval  (:escalate? true, interrupt-before)
                                           +-> :hold              (:hard? true)
```

## Testing

Three tiers of tests:

1. **Governor tests** (`test/defence/governor_test.clj`):
   - Hard violations (unregistered vendor, no-actuation, scope-boundary) always `:hard? true`
   - Escalation invariants (procurement drafting, budget threshold, low confidence) always `:escalate? true`
   - Clean proposals (registered vendor, low-risk op, high confidence) pass through with `:ok? true`

2. **Actor/Graph tests** (`test/defence/actor_test.clj`):
   - A clean request commits and records immediately
   - An unregistered-vendor request holds without committing
   - An escalated request interrupts, then commits after `approve!`
   - A scope-boundary violation (`:op :unknown`) holds hard

3. **Ledger/audit tests**:
   - Every proposal (commit or hold) leaves a ledger entry
   - Ledger is append-only and ordered by time of decision

## References

- ADR-2607011000: itonami Actor Pattern (langgraph StateGraph, Governor, Advisor separation)
- CLAUDE.md: Actors section (itonami pattern spec)
- `cloud-itonami-isco-2411`: Reference implementation (Accounting actor, similar pattern)
- `cloud-itonami-isic-3510`: Reference implementation (Grid Transmission actor, fuller ADR style)
