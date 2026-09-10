(ns defence.store
  "SSoT for the ISIC Rev.5 8422 defence procurement and logistics
  administration actor. Store is a protocol injected into the
  `defence.actor` StateGraph — `MemStore` is the default, deterministic,
  zero-dep backend; a Datomic/kotoba-server-backed implementation can be
  swapped in without touching the actor or governor (itonami actor
  pattern, per ADR-2607011000 / CLAUDE.md's Actors section).

  Domain:

    vendor   — a registered vendor/contractor (:vendor-id, :name, :verified-at)
    record   — a committed procurement or logistics operating record
               (vendor registration, procurement request, equipment
               maintenance plan, logistics coordination) — written ONLY via
               commit-record!, never mutated in place
    ledger   — an append-only audit trail of every proposal/verdict/
               disposition, regardless of outcome (commit or hold)")

(defprotocol Store
  (vendor [s vendor-id])
  (records-of [s vendor-id])
  (ledger [s])
  (register-vendor! [s vendor])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (vendor [_ vendor-id] (get-in @a [:vendors vendor-id]))
  (records-of [_ vendor-id] (filter #(= vendor-id (:vendor-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-vendor! [s vendor]
    (swap! a assoc-in [:vendors (:vendor-id vendor)] vendor) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:vendors {} :records [] :ledger []} seed)))))
