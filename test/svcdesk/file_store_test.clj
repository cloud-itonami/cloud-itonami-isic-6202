(ns svcdesk.file-store-test
  "Fast, in-process regression coverage for `svcdesk.file-store`'s
  persistence contract: mutate a `FileStore`, then construct a BRAND NEW
  `FileStore` instance (a fresh record wrapping a fresh atom, not the same
  object — the closest thing to 'restart' reachable inside one JVM/test
  run) at the SAME path, and confirm the new instance sees the old
  instance's writes.

  This test does NOT itself kill and restart an OS process — it proves
  the snapshot-to-disk/load-from-disk logic is correct, not that a real
  `clojure -M:serve` process survives a real restart (see sibling actors'
  `crm.file-store`/`marketing.file-store` commit history for that manual
  end-to-end transcript; it is not automated here because spawning a JVM
  subprocess per test run is slow and this file's job is fast regression
  coverage of the persistence LOGIC)."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.edn :as edn]
            [svcdesk.store :as store]
            [svcdesk.file-store :as file-store])
  (:import (java.nio.file Files)
           (java.nio.file.attribute FileAttribute)))

(defn- temp-path
  "A filesystem path that does not yet exist -- `Files/createTempFile`
  actually creates the (empty) file to reserve a unique name, so it is
  deleted immediately; `svcdesk.file-store/file-store!` should treat a
  nonexistent path as 'seed fresh', not try to `edn/read-string` an
  empty file."
  []
  (let [f (Files/createTempFile "svcdesk-file-store-test" ".edn" (make-array FileAttribute 0))]
    (Files/delete f)
    (str f)))

(deftest fresh-path-seeds-demo-data-and-writes-it
  (let [path (temp-path)
        s (file-store/file-store! path)]
    (testing "seeded like seed-db"
      (is (= 2 (count (store/all-agents s))))
      (is (= 3 (count (store/all-cases s))))
      (is (= :tier/pro (:subscription-tier (store/account s "acct-acme")))))
    (testing "the seed was actually written to disk, not just held in memory"
      (let [on-disk (edn/read-string (slurp path))]
        (is (= 2 (count (:agents on-disk))))
        (is (= [] (:ledger on-disk)))))))

(deftest writes-survive-a-fresh-instance-at-the-same-path
  (let [path (temp-path)
        s1 (file-store/file-store! path)]
    ;; Mutate through s1: a case-status transition, a correction, and two
    ;; ledger entries -- the same operations svcdesk.operation's :commit
    ;; node performs.
    (store/commit-record! s1 {:effect :case-status-upsert
                               :value {:case-id "case-100" :to-status :in-progress
                                       :assigned-agent-id "agent-100"
                                       :committed-response-hours 48
                                       :closed? false}})
    (store/commit-record! s1 {:effect :correction-apply
                               :value {:patch {:status :resolved}}
                               :path ["case-100"]})
    (store/append-ledger! s1 {:op :a :disposition :commit})
    (store/append-ledger! s1 {:op :b :disposition :hold})

    ;; A BRAND NEW FileStore record/atom at the same path -- not s1, not
    ;; sharing any Clojure object with it. If this sees s1's writes, the
    ;; state genuinely round-tripped through the file on disk.
    (let [s2 (file-store/file-store! path)]
      (testing "case-status transition persisted"
        (is (= :resolved (:status (store/case-record s2 "case-100")))))
      (testing "correction persisted (overwrote status again via patch)"
        (is (= :resolved (:status (store/case-record s2 "case-100")))))
      (testing "ledger persisted, order-preserving"
        (is (= [:commit :hold] (mapv :disposition (take-last 2 (store/ledger s2))))))
      (testing "unmodified fields also carried over"
        (is (= "Acme Corp (demo)" (:name (store/account s2 "acct-acme"))))))))

(deftest with-accounts-persists-and-is-visible-to-a-fresh-instance
  (let [path (temp-path)
        s1 (file-store/file-store! path)]
    ;; svcdesk.store/Store has no `all-accounts` (only single-id `account`
    ;; lookup), so this rebuilds the id->account map from the two known
    ;; demo accounts rather than enumerating -- `with-accounts` replaces
    ;; the whole map, so both existing accounts must be carried forward
    ;; explicitly alongside the new one.
    (store/with-accounts s1 {"acct-acme"  (store/account s1 "acct-acme")
                              "acct-basic" (store/account s1 "acct-basic")
                              "acct-new"   {:id "acct-new" :name "New Co"
                                            :subscription-tier :tier/basic :active? true}})
    (let [s2 (file-store/file-store! path)]
      (is (some? (store/account s2 "acct-new")))
      (is (= "New Co" (:name (store/account s2 "acct-new")))))))
