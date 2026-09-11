(ns svcdesk.policy
  "ServiceGovernor — the independent compliance layer that earns the
  SupportOps-LLM the right to transition a support case's status,
  publish a knowledge-base article, disclose account/case data, or
  resolve a dispute. The LLM has no notion of SLA-entitlement scope,
  case-lifecycle sequence validity, embargoed-content risk, or a
  subscriber's disclosure entitlement, so this MUST be a separate
  system able to *reject* a proposal and fall back to HOLD.

  Ten checks, in priority order. The first six are HARD violations: a
  human approver CANNOT override them. The last four are SOFT/always-
  escalate: they route to a human, who may approve.

    1. rbac                        — does actor-role have permission?
    2. sla-tier-gate                — a genuinely new check kind for this
                                      fleet: a case-status transition
                                      cannot itself commit a response-
                                      time promise FASTER than the
                                      account's ACTIVE subscription tier
                                      already entitles — an SLA upgrade
                                      must go through the billing system
                                      first, never be conjured by a case
                                      update (this actor's analog of
                                      `cloud-itonami-isic-5820`'s
                                      entitlement-scope-gate).
    3. case-status-sequence-gate    — another new check kind: a support
                                      case's status transition must be a
                                      valid forward step (or the
                                      `:cancelled` exit) per
                                      `kotoba.crm.pipeline` — no skipping
                                      `:new` straight to `:resolved`, no
                                      reopening a `:resolved` case back to
                                      `:in-progress` (analog of
                                      -isic-5820's stage-sequence-gate).
    4. double-close-gate            — guards a dedicated `:closed?`
                                      boolean fact rather than a
                                      `:status` value (a design choice
                                      deliberately informed by
                                      ADR-2607071320's status-lifecycle
                                      bug, not merely reused by analogy —
                                      the exact failure mode that caused
                                      that bug, same lesson
                                      -isic-5820 applied).
    5. source-provenance-gate       — case-transition source class must
                                      be in the R0 catalog.
    6. licensed-disclosure          — active subscription, columns
                                      within tier.
    7. confidence floor             — low confidence → escalate.
    8. kb-embargo-gate              — a genuinely new check kind: a
                                      knowledge-base article referencing
                                      unreleased/embargoed product
                                      information → ALWAYS escalate to a
                                      human before publish, regardless of
                                      confidence (never auto-publish
                                      embargoed content).
    9. refund-commitment-gate       — a support-case commitment that
                                      implies a refund/credit → ALWAYS
                                      escalate, regardless of confidence
                                      (this actor's analog of an
                                      SLA-breach-imminent/high-stakes
                                      gate).
   10. dispute-request              — never auto-resolves, any phase."
  (:require [clojure.set :as set]
            [svcdesk.facts :as facts]
            [svcdesk.store :as store]))

;; ───────────────────────── policy tables ─────────────────────────

(def confidence-floor 0.6)

(def permissions
  {:agent            #{:case/transition-status}
   :support-manager  #{:case/transition-status :kb/publish-article :dispute/request}
   :account-holder   #{:disclosure/query}})

(def tier-columns
  (let [base #{:id :account-id :status}
        pro-extra #{:assigned-agent-id}
        ent-extra #{:committed-response-hours :closed?}]
    {:tier/basic      base
     :tier/pro        (into base pro-extra)
     :tier/enterprise (into base (into pro-extra ent-extra))}))

;; ───────────────────────── checks ─────────────────────────

(defn- rbac-violations [{:keys [op]} {:keys [actor-role]}]
  (when-not (contains? (get permissions actor-role #{}) op)
    [{:rule :rbac :detail (str actor-role " は " op " の権限を持たない")}]))

(defn- sla-tier-violations
  [{:keys [op]} proposal st]
  (when (= op :case/transition-status)
    (let [{:keys [case-id committed-response-hours]} (:value proposal)
          c    (store/case-record st case-id)
          acct (when c (store/account st (:account-id c)))]
      (when (and acct committed-response-hours
                 (not (facts/sla-entitled? (:subscription-tier acct) committed-response-hours)))
        [{:rule :sla-tier-gate
          :detail (str "account subscription tier " (:subscription-tier acct)
                       " はまだ " committed-response-hours "時間以内の応答を"
                       "エンタイトルされていない — 先に billing system で"
                       "サブスクリプションを更新すること")}]))))

(defn- case-status-sequence-violations
  [{:keys [op]} proposal st]
  (when (= op :case/transition-status)
    (let [{:keys [case-id to-status]} (:value proposal)
          c (store/case-record st case-id)]
      (when (and c (not (:closed? c))
                 (not (facts/valid-case-transition? (:status c) to-status)))
        [{:rule :case-status-sequence-gate
          :detail (str "case " case-id " の現status " (:status c)
                       " から " to-status " への遷移は無効(スキップまたは逆行)")}]))))

(defn- double-close-violations
  [{:keys [op]} proposal st]
  (when (= op :case/transition-status)
    (let [c (store/case-record st (get-in proposal [:value :case-id]))]
      (when (:closed? c)
        [{:rule :double-close-gate
          :detail (str "case " (:id c) " は既に closed 済み")}]))))

(defn- source-provenance-violations
  [{:keys [op]} proposal]
  (when (= op :case/transition-status)
    (let [src (:source proposal)]
      (when (or (nil? src) (not (facts/class-allowed? (:class src))))
        [{:rule :source-provenance-gate
          :detail (str "出典が無いか許可された出典クラスでない: " (pr-str src))}]))))

(defn- licensed-disclosure-violations
  [{:keys [op]} {:keys [account-id]} proposal st]
  (when (= op :disclosure/query)
    (let [acct (when account-id (store/account st account-id))]
      (if (or (nil? acct) (not (:active? acct)))
        [{:rule :licensed-disclosure :detail (str "有効な subscription が無い: account=" account-id)}]
        (let [allowed (get tier-columns (:subscription-tier acct) #{})
              cols    (set (:columns proposal))
              extra   (set/difference cols allowed)]
          (when (seq extra)
            [{:rule :licensed-disclosure
              :detail (str "subscription tier " (:subscription-tier acct) " に対し過剰な列: " (vec extra))}]))))))

(defn- kb-embargo-imminent?
  [{:keys [op]} proposal st]
  (when (= op :kb/publish-article)
    (let [{:keys [article-id]} (:value proposal)
          art (store/kb-article st article-id)]
      (boolean (:embargoed? art)))))

(defn- refund-commitment-imminent?
  [{:keys [op]} proposal]
  (when (= op :case/transition-status)
    (boolean (:implies-refund-credit? (:value proposal)))))

(defn check
  "Censors a SupportOps-LLM proposal against the policy tables. Returns
   {:ok? bool :violations [..] :confidence c :escalate? bool
    :kb-embargo-imminent? bool :refund-commitment-imminent? bool
    :hard? bool :correction? bool}."
  [request context proposal st]
  (let [hard        (into []
                          (concat (rbac-violations request context)
                                  (sla-tier-violations request proposal st)
                                  (case-status-sequence-violations request proposal st)
                                  (double-close-violations request proposal st)
                                  (source-provenance-violations request proposal)
                                  (licensed-disclosure-violations request context proposal st)))
        conf             (:confidence proposal 0.0)
        low?             (< conf confidence-floor)
        kb-urgent?       (kb-embargo-imminent? request proposal st)
        refund-urgent?   (refund-commitment-imminent? request proposal)
        correction?      (= :dispute/request (:op request))
        hard?            (boolean (seq hard))]
    {:ok?                          (and (not hard?) (not low?) (not kb-urgent?)
                                        (not refund-urgent?) (not correction?))
     :violations                   hard
     :confidence                   conf
     :hard?                        hard?
     :escalate?                    (and (not hard?)
                                        (or low? kb-urgent? refund-urgent? correction?))
     :kb-embargo-imminent?         kb-urgent?
     :refund-commitment-imminent?  refund-urgent?
     :correction?                  correction?}))

(defn hold-fact
  [request context verdict]
  {:t          :policy-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
