(ns svcdesk.llm
  "SupportOps-LLM client — the *contained intelligence node*.

  It normalizes an incoming case-status-transition request into a
  proposal (which case, which status, which agent, what response-time
  commitment, whether it implies a refund/credit), drafts a knowledge-
  base article publish proposal, drafts account/case disclosure column
  sets, and drafts dispute resolutions. CRITICAL: it is a smart-but-
  untrusted advisor. It returns a *proposal*, never a committed status
  transition, publish, or disclosure. Every output is censored
  downstream by `svcdesk.policy` (the ServiceGovernor) before anything
  touches the SSoT or leaves the actor.

  Deterministic mock so the actor graph runs offline and the governor
  contract is exercised end-to-end. In production this calls a real LLM
  (kotoba-llm) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str
     :rationale  str
     :cites      [kw|str ..]
     :source     {:class kw :ref str}|nil
     :effect     kw
     :value      map|nil
     :columns    [kw ..]|nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [langchain.model :as model]
            [svcdesk.store :as store]))

(defn- propose-transition
  "Support-case status-transition proposal — the LLM only normalizes/
  validates the transition (adds no new SLA-entitlement or lifecycle-
  sequence judgment). `:unsourced?` injects the failure mode we must
  defend against: a case transition proposed with no provenance at all."
  [_db {:keys [case-id to-status agent-id committed-response-hours
               implies-refund-credit? source unsourced?]}]
  (let [src (when-not unsourced? source)]
    {:summary   (str "case status transition: " case-id " → " to-status)
     :rationale "出典引用済み case/account データの正規化のみ。新規権限判定なし。"
     :cites     [:case-id :to-status :agent-id]
     :source    src
     :effect    :case-status-upsert
     :value     {:case-id case-id :to-status to-status
                 :assigned-agent-id agent-id
                 :committed-response-hours committed-response-hours
                 :implies-refund-credit? (boolean implies-refund-credit?)
                 :closed? (boolean (#{:closed :cancelled} to-status))}
     :confidence (if unsourced? 0.9 0.95)}))

(defn- propose-kb-publish
  "Knowledge-base article publish proposal. The LLM has no notion of
  embargo risk — `svcdesk.policy`'s kb-embargo-gate always escalates an
  embargoed article regardless of this confidence."
  [_db {:keys [article-id]}]
  {:summary   (str "KB記事公開提案: " article-id)
   :rationale "記事本文の要約・分類のみ。エンバーゴ判定は governor の責務。"
   :cites     [:article-id]
   :source    nil
   :effect    :kb-article-publish
   :value     {:article-id article-id}
   :confidence 0.9})

(defn- propose-disclosure
  "Disclosure column-set proposal for a licensed account-holder query.
  `:greedy?` injects over-disclosure beyond a basic-tier subscription."
  [_db {:keys [account-id greedy?]}]
  (let [base [:id :account-id :status]
        greedy-extra [:assigned-agent-id :committed-response-hours :closed?]]
    {:summary   (str "開示列提案: " account-id)
     :rationale (if greedy? "分析に有用そうな列を広めに含めた。" "subscription tier に必要な最小列のみ。")
     :cites     base
     :source    nil
     :effect    :disclosure-serve
     :columns   (if greedy? (into base greedy-extra) base)
     :confidence 0.9}))

(defn- propose-dispute
  "Dispute/reassignment draft. NEVER auto-applies — `svcdesk.policy` and
  `svcdesk.phase` both structurally force every `:dispute/request` to
  human review."
  [_db {:keys [disputed-field claim]}]
  {:summary   (str "case の " disputed-field " について異議申立てへの解決案ドラフト")
   :rationale (str "申立て内容: " claim "。裏取りは人間レビューで行う。")
   :cites     [disputed-field]
   :source    nil
   :effect    :correction-apply
   :value     {:patch {disputed-field claim}}
   :confidence 0.5})

(defn infer
  [db {:keys [op] :as request}]
  (case op
    :case/transition-status (propose-transition db request)
    :kb/publish-article     (propose-kb-publish db request)
    :disclosure/query       (propose-disclosure db request)
    :dispute/request        (propose-dispute db request)
    {:summary "未対応の操作" :rationale (str op) :cites [] :source nil
     :effect :noop :confidence 0.0}))

;; ───────────────────────── Advisor protocol ─────────────────────────

(defprotocol Advisor
  (-advise [advisor store request] "store + request → proposal map"))

(defn mock-advisor
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたはCRM連携のカスタマーサービスハブの"
       "SupportOpsアドバイザーです。与えられた事実のみに基づき、提案を1つだけ "
       "EDN マップで返します。説明や前置きは一切書かず、EDN だけを出力します。\n"
       "キー: :summary :rationale :cites :source({:class .. :ref ..}か nil) "
       ":effect(:case-status-upsert|:kb-article-publish|:disclosure-serve|"
       ":correction-apply) :value :confidence(0..1)。\n"
       "重要: SLAエンタイトルメントの妥当性判断、caseライフサイクル遷移順序の"
       "妥当性判断、エンバーゴ済みKB記事の公開可否判断はあなたの責務ではありません"
       "(governor が判定します)。"))

(defn- facts-for [st {:keys [op subject case-id account-id article-id]}]
  (case op
    :disclosure/query    {:account (store/account st (or account-id subject))}
    :kb/publish-article  {:article (store/kb-article st (or article-id subject))}
    {:case (store/case-record st (or case-id subject))}))

(defn- parse-proposal
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :source nil :effect :noop :confidence 0.0})))

(defn llm-advisor
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace [request proposal]
  {:t          :supportops-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :source     (:source proposal)
   :confidence (:confidence proposal)})
