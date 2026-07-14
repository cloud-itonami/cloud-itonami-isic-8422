(ns defence.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [defence.store :as store]
            [defence.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-vendor! st {:vendor-id "vendor-1" :name "Defence Supplies Ltd" :verified-at "2026-01-01"})
    st))

(deftest ok-on-clean-procurement-request
  (let [st (fresh-store)
        proposal {:op :register-vendor :effect :propose :confidence 0.9 :stake :low}
        v (governor/check {:vendor-id "vendor-1"} {} proposal st)]
    (is (:ok? v))
    (is (not (:hard? v)))
    (is (not (:escalate? v)))))

(deftest hard-on-unregistered-vendor
  (let [st (fresh-store)
        proposal {:op :register-vendor :effect :propose :confidence 0.9 :stake :low}
        v (governor/check {:vendor-id "no-such-vendor"} {} proposal st)]
    (is (:hard? v))
    (is (some #(= :no-vendor (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        proposal {:op :register-vendor :effect :direct-write :confidence 0.9 :stake :low}
        v (governor/check {:vendor-id "vendor-1"} {} proposal st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest hard-on-scope-boundary-violation
  (let [st (fresh-store)
        proposal {:op :unknown :effect :propose :confidence 0.0 :stake :high}
        v (governor/check {:vendor-id "vendor-1"} {} proposal st)]
    (is (:hard? v))
    (is (some #(= :scope-boundary (:rule %)) (:violations v)))))

(deftest escalates-on-draft-procurement-request
  (let [st (fresh-store)
        proposal {:op :draft-procurement-request :effect :propose :confidence 0.9 :stake :medium}
        v (governor/check {:vendor-id "vendor-1"} {} proposal st)]
    (is (:escalate? v))
    (is (not (:hard? v)))))

(deftest escalates-on-budget-threshold
  (let [st (fresh-store)
        proposal {:op :schedule-equipment-maintenance :effect :propose :confidence 0.9 :stake :medium :budget 2000000}
        v (governor/check {:vendor-id "vendor-1"} {} proposal st)]
    (is (:escalate? v))
    (is (not (:hard? v)))))

(deftest escalates-on-low-confidence
  (let [st (fresh-store)
        proposal {:op :coordinate-logistics :effect :propose :confidence 0.2 :stake :low}
        v (governor/check {:vendor-id "vendor-1"} {} proposal st)]
    (is (:escalate? v))
    (is (not (:hard? v)))))

(deftest store-records-and-ledger-append-only
  (let [st (fresh-store)]
    (store/commit-record! st {:vendor-id "vendor-1" :op :register-vendor})
    (store/append-ledger! st {:disposition :commit})
    (is (= 1 (count (store/records-of st "vendor-1"))))
    (is (= 1 (count (store/ledger st))))))
