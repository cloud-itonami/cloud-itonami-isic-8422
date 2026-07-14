# Business Model

## Overview

This blueprint is designed for a **defence ministry's procurement and logistics administration office** — the administrative layer that supports logistics, vendor management, and procurement documentation.

This is the administrative counterpart to ground operations, supply chain, and vendor relationships. It is **not** a weapons system, operational command tool, or targeting system.

## Customers

- National and regional defence ministries' procurement offices
- Defence logistics coordination centers
- Procurement oversight boards and compliance agencies
- Transparent defence supply chain consortia

## Revenue Model

Open-source reference implementation:
- AGPL-3.0-or-later license
- Commercial licensing or support services available by arrangement
- Deployment by procurement office IT, with optional training and audit services

## Technology Stack

- `langgraph.graph/state-graph` for the actor state machine
- `kotoba-lang/robotics` for mission scheduling and robot actions
- Datomic or kotoba-server backend for persistent audit ledger
- Human-in-the-loop checkpointing for escalated proposals

## Scope

**In scope:**
- Vendor/contractor verification and registration
- Procurement request intake and drafting
- Equipment maintenance scheduling
- Non-combat logistics coordination (transport, warehousing, supply chain)
- Budget-line tracking
- Audit trail and compliance documentation

**Explicitly out of scope:**
- Personnel deployment or command authority
- Weapons procurement or targeting systems
- Classified or operational military intelligence
- Lethal autonomous decisions

## Assumptions

1. The operator (procurement officer) is accountable for final approvals and audit evidence.
2. The actor supports the operator's decision-making, never replaces it for high-risk ops.
3. Vendors and counterparties are verified before procurement is initiated.
4. Audit trail is immutable and reviewed regularly by compliance auditors.

## References

See [docs/adr/0001-architecture.md](docs/adr/0001-architecture.md) for technical architecture.
