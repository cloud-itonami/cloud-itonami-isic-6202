(ns svcdesk.policy-contract-test
  "The governor contract as executable tests. Single invariant under
  test: SupportOps-LLM never transitions/publishes/discloses/resolves a
  record the ServiceGovernor would reject."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [svcdesk.store :as store]
            [svcdesk.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def agent-ctx {:actor-id "agent-1" :actor-role :agent :phase 3})
(def manager {:actor-id "mg-1" :actor-role :support-manager :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(deftest authorized-transition-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :case/transition-status :subject "case-100" :case-id "case-100"
                   :to-status :in-progress :agent-id "agent-100" :committed-response-hours 48
                   :source {:class :case-management-log :ref "demo"}}
                  agent-ctx)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= :in-progress (:status (store/case-record db "case-100"))))
    (is (= 1 (count (store/ledger db))))))

(deftest unauthorized-role-is-held
  (let [[db actor] (fresh)
        res (exec-op actor "t2"
                  {:op :case/transition-status :subject "case-100" :case-id "case-100"
                   :to-status :in-progress :agent-id "agent-100"
                   :source {:class :case-management-log :ref "demo"}}
                  {:actor-id "sub-1" :actor-role :account-holder :phase 3})]
    (is (= :hold (get-in res [:state :disposition])))
    (is (= [:rbac] (-> (store/ledger db) first :basis)))))

(deftest sla-tier-violation-is-held
  (testing "committing a response time faster than the account's subscription tier entitles → HOLD"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :case/transition-status :subject "case-300" :case-id "case-300"
                     :to-status :resolved :agent-id "agent-200" :committed-response-hours 4
                     :source {:class :case-management-log :ref "demo"}}
                    agent-ctx)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:sla-tier-gate} (-> (store/ledger db) first :basis)))
      (is (= :in-progress (:status (store/case-record db "case-300")))))))

(deftest case-status-sequence-violation-is-held
  (testing "skipping ahead in the case lifecycle (in-progress straight to closed) → HOLD"
    (let [[db actor] (fresh)
          res (exec-op actor "t4"
                    {:op :case/transition-status :subject "case-200" :case-id "case-200"
                     :to-status :closed :agent-id "agent-100"
                     :source {:class :case-management-log :ref "demo"}}
                    agent-ctx)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:case-status-sequence-gate} (-> (store/ledger db) first :basis)))
      (is (= :in-progress (:status (store/case-record db "case-200")))))))

(deftest double-close-violation-is-held
  (testing "a second transition proposal on an already-closed case → HOLD"
    (let [[db actor] (fresh)
          _step1 (exec-op actor "t5a"
                       {:op :case/transition-status :subject "case-200" :case-id "case-200"
                        :to-status :resolved :agent-id "agent-100" :committed-response-hours 12
                        :source {:class :case-management-log :ref "demo"}}
                       agent-ctx)
          _step2 (exec-op actor "t5b"
                       {:op :case/transition-status :subject "case-200" :case-id "case-200"
                        :to-status :closed :agent-id "agent-100"
                        :source {:class :case-management-log :ref "demo"}}
                       agent-ctx)
          _ (is (true? (:closed? (store/case-record db "case-200"))))
          res (exec-op actor "t5c"
                    {:op :case/transition-status :subject "case-200" :case-id "case-200"
                     :to-status :cancelled :agent-id "agent-100"
                     :source {:class :case-management-log :ref "demo"}}
                    agent-ctx)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:double-close-gate} (-> (store/ledger db) last :basis))))))

(deftest uncontracted-disclosure-is-held
  (let [[db actor] (fresh)
        res (exec-op actor "t7"
                  {:op :disclosure/query :subject "acct-ghost" :account-id "acct-ghost"}
                  {:actor-id "sub-2" :actor-role :account-holder :account-id "acct-ghost" :phase 3})]
    (is (= :hold (get-in res [:state :disposition])))
    (is (some #{:licensed-disclosure} (-> (store/ledger db) first :basis)))))

(deftest over-disclosure-beyond-tier-is-held
  (let [[db actor] (fresh)
        res (exec-op actor "t8"
                  {:op :disclosure/query :subject "acct-basic" :account-id "acct-basic" :greedy? true}
                  {:actor-id "sub-1" :actor-role :account-holder :account-id "acct-basic" :phase 3})]
    (is (= :hold (get-in res [:state :disposition])))
    (is (some #{:licensed-disclosure} (-> (store/ledger db) first :basis)))))

(deftest kb-embargo-escalates-then-human-decides
  (testing "an embargoed knowledge-base article publish always interrupts for human approval"
    (let [[db actor] (fresh)
          r1 (exec-op actor "t9"
                   {:op :kb/publish-article :subject "kb-200" :article-id "kb-200"}
                   manager)]
      (is (= :interrupted (:status r1)))
      (is (= :kb-embargo-imminent (-> r1 :state :audit last :reason)))
      (let [r2 (g/run* actor {:approval {:status :approved :by "manager-1"}}
                       {:thread-id "t9" :resume? true})]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (= :published (:status (store/kb-article db "kb-200"))))))))

(deftest refund-commitment-escalates-then-human-decides
  (testing "a case commitment implying a refund/credit always interrupts for human approval"
    (let [[db actor] (fresh)
          r1 (exec-op actor "t10"
                   {:op :case/transition-status :subject "case-100" :case-id "case-100"
                    :to-status :in-progress :agent-id "agent-100" :committed-response-hours 12
                    :implies-refund-credit? true
                    :source {:class :customer-inbound-message :ref "demo"}}
                   manager)]
      (is (= :interrupted (:status r1)))
      (is (= :refund-commitment-imminent (-> r1 :state :audit last :reason)))
      (let [r2 (g/run* actor {:approval {:status :approved :by "manager-1"}}
                       {:thread-id "t10" :resume? true})]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (= :in-progress (:status (store/case-record db "case-100"))))))))

(deftest dispute-request-always-escalates-regardless-of-confidence
  (let [[_db actor] (fresh)
        r1 (exec-op actor "t11"
                 {:op :dispute/request :subject "case-100" :disputed-field :status :claim :new}
                 manager)]
    (is (= :interrupted (:status r1)))
    (is (= :dispute-request (-> r1 :state :audit last :reason)))
    (let [r2 (g/run* actor {:approval {:status :approved :by "manager-1"}}
                     {:thread-id "t11" :resume? true})]
      (is (= :commit (get-in r2 [:state :disposition]))))))
