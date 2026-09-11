(ns svcdesk.store-contract-test
  (:require [clojure.test :refer [deftest is testing]]
            [svcdesk.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "田中 太郎(デモ)" (:name (store/svc-agent s "agent-100"))))
      (is (= :tier/pro (:subscription-tier (store/account s "acct-acme"))))
      (is (= :tier/basic (:subscription-tier (store/account s "acct-basic"))))
      (is (= :in-progress (:status (store/case-record s "case-300"))))
      (is (= :draft (:status (store/kb-article s "kb-100"))))
      (is (true? (:embargoed? (store/kb-article s "kb-200"))))
      (is (= 2 (count (store/all-agents s))))
      (is (= 3 (count (store/all-cases s)))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "case-status upsert commits"
        (store/commit-record! s {:effect :case-status-upsert
                                 :value {:case-id "case-100" :to-status :in-progress
                                         :assigned-agent-id "agent-100"
                                         :committed-response-hours 48 :closed? false}})
        (is (= :in-progress (:status (store/case-record s "case-100")))))
      (testing "kb-article publish commits"
        (store/commit-record! s {:effect :kb-article-publish :value {:article-id "kb-100"}})
        (is (= :published (:status (store/kb-article s "kb-100")))))
      (testing "correction-apply patches the case"
        (store/commit-record! s {:effect :correction-apply
                                 :value {:patch {:status :new}}
                                 :path ["case-100"]})
        (is (= :new (:status (store/case-record s "case-100")))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (take-last 2 (store/ledger s)))))))))

(deftest account-lookup
  (doseq [[label s] (backends)]
    (testing label
      (is (true? (:active? (store/account s "acct-acme"))))
      (is (nil? (store/account s "acct-ghost"))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/svc-agent s "nope")))
    (is (= [] (store/all-agents s)))
    (is (= [] (store/ledger s)))))
