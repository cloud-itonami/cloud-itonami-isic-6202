(ns svcdesk.facts-test
  (:require [clojure.test :refer [deftest is]]
            [svcdesk.facts :as facts]))

(deftest class-allowed?-rejects-unlisted-classes
  (is (facts/class-allowed? :case-management-log))
  (is (facts/class-allowed? :customer-inbound-message))
  (is (facts/class-allowed? :billing-system-webhook))
  (is (not (facts/class-allowed? :inference)))
  (is (not (facts/class-allowed? nil))))

(deftest sla-tier-at-least?-orders-correctly
  (is (facts/sla-tier-at-least? :tier/enterprise :tier/basic))
  (is (facts/sla-tier-at-least? :tier/pro :tier/pro))
  (is (not (facts/sla-tier-at-least? :tier/basic :tier/pro)))
  (is (not (facts/sla-tier-at-least? :tier/pro :tier/enterprise))))

(deftest sla-entitled?-checks-min-response-hours
  (is (facts/sla-entitled? :tier/basic 48))
  (is (not (facts/sla-entitled? :tier/basic 47)))
  (is (facts/sla-entitled? :tier/enterprise 2))
  (is (not (facts/sla-entitled? :tier/enterprise 1)))
  (is (facts/sla-entitled? :tier/pro nil) "nil commitment is not a promise, so it's not a violation"))

(deftest valid-case-transition?-forbids-skip-and-reverse
  (is (facts/valid-case-transition? :new :in-progress))
  (is (facts/valid-case-transition? :in-progress :resolved))
  (is (facts/valid-case-transition? :resolved :closed))
  (is (not (facts/valid-case-transition? :new :resolved)) "no skipping ahead")
  (is (not (facts/valid-case-transition? :resolved :in-progress)) "no reopening/reversing")
  (is (facts/valid-case-transition? :new :cancelled) "exit status reachable from any non-terminal status")
  (is (not (facts/valid-case-transition? :closed :cancelled)) "already-terminal status accepts no further transition"))

(deftest coverage-is-honest-not-aspirational
  (let [c (facts/coverage)]
    (is (= 3 (count (:source-classes c))) "3 provenance classes")
    (is (= 3 (:sla-tier-count c)))
    (is (= 4 (:case-status-count c)))))
