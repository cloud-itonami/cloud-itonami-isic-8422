(ns defence.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [defence.actor :as actor]
            [defence.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-vendor! st {:vendor-id "vendor-1" :name "Defence Supplies Ltd" :verified-at "2026-01-01"})
    st))

(deftest commits-a-clean-low-risk-request
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:vendor-id "vendor-1" :op :coordinate-logistics :stake :low}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "vendor-1"))))))

(deftest holds-on-unregistered-vendor-without-committing
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:vendor-id "no-such-vendor" :op :coordinate-logistics :stake :low}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :done (:status result)))
    (is (nil? (get-in result [:state :record])))
    (is (empty? (store/records-of st "no-such-vendor")))
    (is (= :hold (:disposition (:state result))))))

(deftest interrupts-then-commits-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        ;; budget commitment above threshold always escalates (governor invariant)
        request {:vendor-id "vendor-1" :op :schedule-equipment-maintenance :stake :high :budget 2000000}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "vendor-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (some? (get-in resumed [:state :record])))
      (is (= 1 (count (store/records-of st "vendor-1")))))))

(deftest holds-on-scope-boundary-violation
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        ;; :unknown op (out-of-scope) should hard-hold
        request {:vendor-id "vendor-1" :op :unknown :stake :high}
        result (actor/run-request! graph request {} "thread-4")]
    (is (= :done (:status result)))
    (is (nil? (get-in result [:state :record])))
    (is (empty? (store/records-of st "vendor-1")))
    (is (= :hold (:disposition (:state result))))))
