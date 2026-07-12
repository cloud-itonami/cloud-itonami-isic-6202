(ns svcdesk.phase-test
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [svcdesk.store :as store]
            [svcdesk.operation :as op]))

(def agent-ctx {:actor-id "agent-1" :actor-role :agent})
(def manager {:actor-id "mg-1" :actor-role :support-manager})

(def clean-transition
  {:op :case/transition-status :subject "case-100" :case-id "case-100"
   :to-status :in-progress :agent-id "agent-100" :committed-response-hours 48
   :source {:class :case-management-log :ref "demo"}})

(def clean-disclosure
  {:op :disclosure/query :subject "acct-acme" :account-id "acct-acme"})

(def clean-kb-publish
  {:op :kb/publish-article :subject "kb-100" :article-id "kb-100"})

(def dispute-req
  {:op :dispute/request :subject "case-100" :disputed-field :status :claim :new})

(defn- run [phase req ctx]
  (let [s (store/seed-db)
        actor (op/build s)]
    [s (g/run* actor {:request req :context (assoc ctx :phase phase)}
               {:thread-id (str "ph-" phase "-" (:op req))})]))

(deftest phase0-holds-all-writes
  (let [[s res] (run 0 clean-transition agent-ctx)]
    (is (= :hold (get-in res [:state :disposition])))
    (is (= :phase-disabled (-> (store/ledger s) first :phase-reason)))))

(deftest phase0-allows-governed-reads
  (let [[_ res] (run 0 clean-disclosure {:actor-id "sub-1" :actor-role :account-holder :account-id "acct-acme"})]
    (is (= :commit (get-in res [:state :disposition])))))

(deftest phase1-forces-approval-on-clean-transition
  (let [[_ res] (run 1 clean-transition agent-ctx)]
    (is (= :interrupted (:status res)))
    (is (= :phase-approval (-> res :state :audit last :reason)))))

(deftest phase3-auto-commits-clean-transition
  (let [[s res] (run 3 clean-transition agent-ctx)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= :in-progress (:status (store/case-record s "case-100"))))))

(deftest phase3-auto-commits-clean-non-embargoed-kb-publish
  (let [[s res] (run 3 clean-kb-publish manager)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= :published (:status (store/kb-article s "kb-100"))))))

(deftest governor-hold-beats-phase
  (testing "a hard governor violation (SLA entitlement exceeded) holds even in the most permissive phase"
    (let [[_ res] (run 3 {:op :case/transition-status :subject "case-300" :case-id "case-300"
                          :to-status :resolved :agent-id "agent-200" :committed-response-hours 4
                          :source {:class :case-management-log :ref "demo"}}
                       agent-ctx)]
      (is (= :hold (get-in res [:state :disposition]))))))

(deftest dispute-request-never-auto-commits-at-any-phase
  (doseq [ph [0 1 2 3]]
    (let [[_ res] (run ph dispute-req manager)]
      (is (not= :commit (get-in res [:state :disposition]))
          (str "phase " ph " must not auto-commit a dispute")))))
