# cloud-itonami-isic-8422

Open Occupation Blueprint for **ISIC Rev.5 8422**: Defence Procurement and Logistics Administration.

This repository designs a forkable OSS business for a defence ministry's procurement and logistics administration: a document-handling and verification robot performs vendor due diligence, procurement request drafting, and logistics coordination under a governor-gated actor, so a defence procurement office keeps its own procurement records and audit trail instead of renting a closed logistics SaaS.

## IMPORTANT: SCOPE BOUNDARIES

**This actor is EXPLICITLY NOT a weapons system, operational command tool, or targeting system.**

### What this actor DOES

- Administrative and logistical operations only:
  - Vendor/contractor verification and registration
  - Procurement request intake and drafting
  - Equipment maintenance scheduling
  - Non-combat logistics coordination (transport, warehousing, supply chain)
  - Budget-line tracking and commitment monitoring
  - Audit trail and compliance documentation

### What this actor DOES NOT (hard boundaries, permanently out of scope)

These operations are **permanently forbidden** — they are not gated by risk level or approval hierarchy, they cannot be escalated for human override, and the actor's proposal vocabulary has no path to construct them. A closed allowlist enforces this at the governance layer:

- **Personnel deployment, command, or authority decisions** — the actor has no notion of tactical decisions, unit assignments, or personnel movement
- **Weapons systems, munitions, or targeting** — no procurement of weapons, explosives, or systems designed for combat; no engagement logic; no targeting parameters
- **Classified or operational military activity** — no access to classified intelligence, operational plans, or real-time combat data; no operations coordination beyond pure administrative logistics
- **Lethal autonomous decisions** — no authority to make or propose any decision whose effect is kinetic, destructive, or results in loss of life

These are not "high-risk operations requiring escalation" — they are entirely outside the actor's design vocabulary. The governor will **permanently :hold** any proposal that touches these categories (it is not a matter of confidence, approval chain, or budget threshold).

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a document-handling and verification robot performs vendor due diligence, procurement request drafting, and logistics coordination under an actor that proposes actions and an independent **Defence Procurement Governor** that gates them. The governor never dispatches a robot action itself; `:high`/`:safety-critical` actions (such as budget commitments above a threshold, or contract award finalization) require human sign-off.

A live sample of the operator console (robotics safety console, shared template) is rendered in [docs/samples/operator-console.html](docs/samples/operator-console.html) — pure-data HTML output of `kotoba.robotics.ui`.

## Core Contract

```text
procurement request + vendor identity + logistics data
        |
        v
Defence Procurement Advisor -> Defence Procurement Governor -> vendor verification, procurement request, logistics plan, or human approval
        |
        v
robot actions (gated) + procurement record + logistics record + audit ledger
```

No automated advice can dispatch a procurement action the governor refuses, verify a vendor outside its registered scope, or publish a procurement record without governor approval and audit evidence.

## Capability layer

Resolves via [`kotoba-lang/industry`](https://github.com/kotoba-lang/industry)
(ISIC `8422`). Required capabilities:

- :robotics
- :identity
- :forms
- :dmn
- :bpmn
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## Reference implementation (`:maturity :implemented`)

Full itonami Actor pattern (per ADR-2607011000 / CLAUDE.md's Actors
section): a real
[`kotoba-lang/langgraph`](https://github.com/kotoba-lang/langgraph)
`StateGraph`, with the Advisor and Governor as distinct graph nodes and
human-in-the-loop interrupt/resume via checkpointing.

```text
:intake -> :advise -> :govern -> :decide -+-> :commit            (:ok? true)
                                           +-> :request-approval   (:escalate? true, interrupt-before)
                                           +-> :hold               (:hard? true)
```

- `src/defence/store.cljc` — `Store` protocol + `MemStore`:
  registered vendors, committed procurement records, an append-only audit ledger.
- `src/defence/advisor.cljc` — `Advisor` protocol; `mock-advisor`
  (deterministic, default) proposes a procurement operation from a
  request; `llm-advisor` wraps a `langchain.model/ChatModel` — either
  way the advisor only ever produces a `:propose`-effect proposal,
  never a committed record, and LLM parse failures always yield
  `confidence 0.0` (forces escalation, never fabricated confidence).
- `src/defence/governor.cljc` — `DefenceProcurementGovernor/check`: a pure
  function, wired as its own `:govern` node. Hard invariants
  (unregistered vendor, a proposal whose `:effect` isn't `:propose`, any
  proposal touching weapons/personnel/classified operations) always
  route to `:hold`. Escalation invariants (budget commitment above
  threshold, contract award finalization, or low advisor confidence)
  always route to `:request-approval` — an `interrupt-before` node that
  the graph checkpoints and only resumes on explicit human approval
  (`actor/approve!`).
- `src/defence/actor.cljc` — `build-graph`, `run-request!`,
  `approve!`: the `langgraph.graph/state-graph` wiring itself.

```bash
clojure -M:test
```

This is what backs this repo's `:maturity :implemented` entry in
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry).

## License

AGPL-3.0-or-later.
