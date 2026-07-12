(ns svcdesk.facts
  "R0 provenance/authority catalog for the CRM/subscription-integrated
  customer-service-hub actor (HubSpot Service Hub/Salesforce Service
  Cloud-class SaaS platform business, ISIC Rev.4 6202 narrowed to
  hosted support-case + SLA-entitlement + knowledge-base governance) —
  the ONLY source classes, SLA-entitlement tiers, and case-lifecycle
  shape the ServiceGovernor will accept (honesty over coverage, same
  discipline as sibling actors' facts catalogs, e.g.
  `cloud-itonami-isic-5820`'s `crm.facts`).

  Three closed sets/tables:
    1. `allowed-source-classes` — where a case-transition/disclosure
       proposal's evidence came from (a logged case-management-system
       entry, an inbound customer email/chat transcript, or the billing
       system's own webhook — the account's subscription-tier
       authoritative source). A proposal citing anything outside this
       set is rejected outright.
    2. `sla-tiers` + `sla-min-response-hours` — an ordered subscription/
       SLA-entitlement scale (the same least-privilege framing
       `cloud-itonami-isic-5820`'s discount-authority-tiers and
       `cloud-itonami-isic-6209`'s access-tier-clearance apply to their
       own domains) mapping an account's active subscription tier to
       the FASTEST response-time commitment (in hours) a case may be
       promised without escalation — committing anything faster than
       the tier entitles is the failure mode this actor exists to
       block, never merely 'the LLM felt generous'.
    3. `case-status-order` / `case-exit-statuses` — the support-case
       lifecycle shape, delegated to the shared `kotoba.crm.pipeline`
       technical commons (the same generic stage-transition validator
       `cloud-itonami-isic-5820` uses for its sales pipeline — a support
       case's new → in-progress → resolved → closed lifecycle is
       directly analogous to a linear sales-pipeline shape)."
  (:require [kotoba.crm.pipeline :as pipeline]))

(def allowed-source-classes
  #{:case-management-log :customer-inbound-message :billing-system-webhook})

(def sla-tiers
  "Ordered subscription/SLA-entitlement scale, low to high."
  [:tier/basic :tier/pro :tier/enterprise])

(def sla-min-response-hours
  "The FASTEST response-time commitment (hours) each subscription tier
  entitles. A case may be promised this many hours or MORE without
  escalation; promising fewer hours than the account's tier entitles is
  a HARD violation (`sla-tier-gate`) — analogous to
  `cloud-itonami-isic-5820`'s entitlement-scope-gate, but for a response-
  time commitment rather than a feature/seat."
  {:tier/basic 48 :tier/pro 12 :tier/enterprise 2})

(def case-status-order
  [:new :in-progress :resolved :closed])

(def case-exit-statuses #{:cancelled})

(def ^:private sla-rank
  (into {} (map-indexed (fn [i t] [t i])) sla-tiers))

(defn sla-tier-at-least? [account-tier required-tier]
  (>= (get sla-rank account-tier -1) (get sla-rank required-tier 0)))

(defn class-allowed? [source-class]
  (contains? allowed-source-classes source-class))

(defn sla-entitled?
  "True iff `committed-hours` is a response-time commitment the
  account's `sla-tier` is ALREADY entitled to (i.e. not faster than the
  tier's minimum)."
  [sla-tier committed-hours]
  (or (nil? committed-hours)
      (>= committed-hours (get sla-min-response-hours sla-tier ##Inf))))

(defn valid-case-transition?
  "Delegates to `kotoba.crm.pipeline` — no skipping ahead (e.g.
  `:new` straight to `:resolved`), no transition out of a terminal
  status, `:cancelled` reachable from any non-terminal status."
  [from-status to-status]
  (pipeline/valid-transition? case-status-order case-exit-statuses
                               from-status to-status))

(defn coverage
  "Honest, machine-checkable report of what R0 actually covers."
  []
  {:source-classes allowed-source-classes
   :sla-tier-count (count sla-tiers)
   :case-status-count (count case-status-order)
   :note (str "R0 scope: 3 provenance classes, "
              (count sla-tiers)
              "-level SLA/subscription entitlement scale, "
              (count case-status-order)
              "-status linear support-case lifecycle plus 1 exit status "
              "(:cancelled). Extend only by appending a documented "
              "provenance class, SLA tier, or case status — never "
              "fabricate either.")})
