# Operator Guide

## Overview

This guide is for procurement officers and logistics administrators using the Defence Procurement Actor.

## Basic Workflow

### 1. Register a Vendor

Before initiating any procurement from a vendor, register them:

```clojure
(store/register-vendor! store
  {:vendor-id "acme-defense-1"
   :name "ACME Defence Supplies Inc."
   :verified-at "2026-01-15"})
```

The actor **will not process** requests from unregistered vendors (this is a hard, non-overridable block).

### 2. Submit a Procurement Request

Issue a procurement request:

```clojure
(actor/run-request!
  graph
  {:vendor-id "acme-defense-1"
   :op :draft-procurement-request
   :stake :medium
   :description "100 replacement engine components"}
  {}
  "thread-1")
```

The actor will advise on the request and route it based on risk:
- **Low risk** (high confidence, small budget): auto-commits
- **Higher risk** (budget above threshold, low confidence): escalates for human approval

### 3. Review Escalated Proposals

When a proposal escalates (status = `:interrupted`), a human operator must review:

```clojure
(let [result (actor/run-request! graph request {} "thread-2")]
  (when (= :interrupted (:status result))
    ;; Operator reviews (:state result) and decides
    (if operator-approves?
      (actor/approve! graph "thread-2")  ;; Resume and commit
      ;; ... else: proposal remains held, no commit
      )))
```

Escalations include:
- Procurement requests (require human review of scope, vendor, terms)
- Budget commitments above 1,000,000 (configurable)
- Low-confidence proposals

### 4. Audit the Ledger

All proposals (committed or held) leave an audit trail:

```clojure
(store/ledger store)
;; Returns: [{:disposition :commit :record {...}}
;;           {:disposition :hold :verdict {...}}
;;           ...]
```

Review the ledger regularly to confirm:
- Hard holds are applied consistently
- No out-of-scope proposals reached human approval
- Budget commitments are tracked

## Understanding Hard Holds

Some proposals **never proceed**, no matter the confidence or approval chain:

1. **Unregistered vendor**: a request from a vendor not in the store is always held.
2. **Direct actuation**: a proposal claiming direct store writes is always held (this is a safety feature; the actor only produces `:propose` effects).
3. **Scope boundary**: any proposal touching weapons, personnel, classified operations, or lethal decisions is **permanently held**. This is not a "high-risk op requiring escalation" — it is entirely outside the actor's vocabulary.

## Scope Reminders

This actor is **procurement and logistics administration only**. It has **no capability** to:

- Deploy or command personnel
- Authorize weapons procurement or targeting
- Access classified or operational intelligence
- Execute kinetic or lethal actions

If you attempt a procurement that touches these areas, the actor will hold it. This is by design, not a limitation.

## Troubleshooting

### "Proposal held unexpectedly"

Check the verdict's `:violations`. Common causes:

- Vendor is not registered: register them first with `register-vendor!`
- Operation is out-of-scope: check the allowed operations list
- Confidence is too low: escalate to a human for review

### "Ledger not updating"

Ensure you're calling `append-ledger!` in the `:commit` and `:hold` nodes. The default actor implementation does this automatically; custom governors should also log.

## References

- [docs/adr/0001-architecture.md](docs/adr/0001-architecture.md) for design details
- [README.md](../README.md) for scope boundaries
- Deployment guide (forthcoming)
