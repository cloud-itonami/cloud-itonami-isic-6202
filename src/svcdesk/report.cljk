(ns svcdesk.report
  "Disclosure rendering — output as a GOVERNED read. The column set is
  whatever the ServiceGovernor's licensed-disclosure gate approved for
  the caller's subscription tier."
  (:require [svcdesk.store :as store]))

(defn render-case
  [db case-id columns]
  (let [c (store/case-record db case-id)
        cell (fn [col]
               (case col
                 :id                       case-id
                 :account-id               (:account-id c)
                 :status                   (:status c)
                 :assigned-agent-id        (:assigned-agent-id c)
                 :committed-response-hours (:committed-response-hours c)
                 :closed?                  (:closed? c)
                 nil))]
    (into {} (map (juxt identity cell)) columns)))
