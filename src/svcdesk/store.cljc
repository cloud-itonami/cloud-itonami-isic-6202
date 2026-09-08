(ns svcdesk.store
  "SSoT for the CRM/subscription-integrated customer-service-hub actor,
  behind a `Store` protocol so the backend is a swap, not a rewrite:

    - `MemStore`     — atom of Datomic-shaped EDN. The deterministic
                       default for dev/tests/demo (no deps).
    - `DatomicStore` — backed by `langchain.db`, a Datomic-API-compatible
                       EAV store. Pure `.cljc`, so it runs offline AND can
                       be pointed at a real Datomic Local or a
                       kotoba-server pod.

  Both implement the same protocol and pass the same contract
  (test/svcdesk/store_contract_test.clj) — the actor, the
  ServiceGovernor and the audit ledger never know which SSoT they run on.

  Entity shapes: an agent (name only — R0 does not model per-agent
  authority tiers, see docs/business-model.md Honest scope), an account
  (subscription-tier, active? — the CRM/subscription linkage this actor
  exists to enforce), a support case (account/status/assigned-agent/
  committed-response-hours/closed?), a knowledge-base article (title/
  status/embargoed?). There is NO field anywhere in this schema for
  discount-authority, sales-pipeline stage, or booked revenue — this
  actor covers support-case + SLA-entitlement + knowledge-base
  governance only; sales-pipeline/subscription-commerce
  (`cloud-itonami-isic-5820`) and IT-helpdesk ticket routing
  (`cloud-itonami-isic-6209`) are explicitly out of scope for R0 (see
  README).

  The ledger stays append-only on every backend."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [kotoba.lang.text :as str]
            [langchain.db :as d]))

(defprotocol Store
  (svc-agent [s id])
  (all-agents [s])
  (account [s id])
  (case-record [s id])
  (all-cases [s])
  (kb-article [s id])
  (ledger [s])
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision/disclosure fact")
  (with-agents [s agents]         "replace/seed agents (map id→agent)")
  (with-accounts [s accounts]     "replace/seed accounts (map id→account)")
  (with-cases [s cases]           "replace/seed cases (map id→case)")
  (with-kb-articles [s articles]  "replace/seed kb articles (map id→article)"))

;; ───────────────────────── demo data (fictitious) ─────────────────────

(defn demo-data
  "A small, entirely fictitious dataset so the actor + tests run offline.
  `case-300` sits at `:in-progress` on a `:tier/basic` account purely to
  exercise the sla-tier-gate governor gate."
  []
  {:agents
   {"agent-100" {:id "agent-100" :name "田中 太郎(デモ)"}
    "agent-200" {:id "agent-200" :name "Jane Doe (demo)"}}
   :accounts
   {"acct-acme"  {:id "acct-acme"  :name "Acme Corp (demo)"  :subscription-tier :tier/pro :active? true}
    "acct-basic" {:id "acct-basic" :name "Basic Co (demo)"    :subscription-tier :tier/basic :active? true}}
   :cases
   {"case-100" {:id "case-100" :account-id "acct-acme" :status :new
                :assigned-agent-id nil :committed-response-hours nil :closed? false}
    "case-200" {:id "case-200" :account-id "acct-acme" :status :in-progress
                :assigned-agent-id "agent-100" :committed-response-hours 12 :closed? false}
    "case-300" {:id "case-300" :account-id "acct-basic" :status :in-progress
                :assigned-agent-id "agent-200" :committed-response-hours 48 :closed? false}}
   :kb-articles
   {"kb-100" {:id "kb-100" :title "パスワードリセット手順(デモ)" :status :draft :embargoed? false}
    "kb-200" {:id "kb-200" :title "次期メジャーリリース新機能(デモ、未発表)" :status :draft :embargoed? true}}})

;; ───────────────────────── MemStore (default) ─────────────────────────

(defrecord MemStore [a]
  Store
  (svc-agent [_ id] (get-in @a [:agents id]))
  (all-agents [_] (sort-by :id (vals (:agents @a))))
  (account [_ id] (get-in @a [:accounts id]))
  (case-record [_ id] (get-in @a [:cases id]))
  (all-cases [_] (sort-by :id (vals (:cases @a))))
  (kb-article [_ id] (get-in @a [:kb-articles id]))
  (ledger [_] (:ledger @a))
  (commit-record! [s {:keys [effect path value]}]
    (case effect
      :case-status-upsert
      (swap! a update-in [:cases (:case-id value)]
             merge {:status (:to-status value)
                    :assigned-agent-id (:assigned-agent-id value)
                    :committed-response-hours (:committed-response-hours value)
                    :closed? (boolean (:closed? value))})
      :kb-article-publish
      (swap! a update-in [:kb-articles (:article-id value)] merge {:status :published})
      :correction-apply
      (swap! a update-in [:cases (first path)] merge (:patch value))
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-agents [s ags]        (when (seq ags) (swap! a assoc :agents ags)) s)
  (with-accounts [s accts]    (when (seq accts) (swap! a assoc :accounts accts)) s)
  (with-cases [s cs]          (when (seq cs) (swap! a assoc :cases cs)) s)
  (with-kb-articles [s arts]  (when (seq arts) (swap! a assoc :kb-articles arts)) s))

(defn seed-db
  "A MemStore seeded with the demo data. The deterministic default."
  []
  (->MemStore (atom (assoc (demo-data) :ledger []))))

;; ───────────────────────── DatomicStore (langchain.db) ─────────────────

(def ^:private schema
  {:agent/id        {:db/unique :db.unique/identity}
   :account/id      {:db/unique :db.unique/identity}
   :case/id         {:db/unique :db.unique/identity}
   :kb-article/id   {:db/unique :db.unique/identity}
   :ledger/seq      {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

(defn- agent->tx [{:keys [id name]}]
  (cond-> {:agent/id id}
    name (assoc :agent/name name)))

(defn- pull->agent [m]
  (when (:agent/id m)
    {:id (:agent/id m) :name (:agent/name m)}))

(def ^:private agent-pull [:agent/id :agent/name])

(defn- account->tx [{:keys [id name subscription-tier active?]}]
  {:account/id id :account/name name
   :account/subscription-tier subscription-tier :account/active active?})

(defn- pull->account [m]
  (when (:account/id m)
    {:id (:account/id m) :name (:account/name m)
     :subscription-tier (:account/subscription-tier m) :active? (:account/active m)}))

(def ^:private account-pull
  [:account/id :account/name :account/subscription-tier :account/active])

(defn- case->tx [{:keys [id account-id status assigned-agent-id committed-response-hours closed?]}]
  {:case/id id :case/account-id account-id
   :case/status status :case/assigned-agent-id assigned-agent-id
   :case/committed-response-hours committed-response-hours :case/closed (boolean closed?)})

(defn- pull->case [m]
  (when (:case/id m)
    {:id (:case/id m) :account-id (:case/account-id m)
     :status (:case/status m) :assigned-agent-id (:case/assigned-agent-id m)
     :committed-response-hours (:case/committed-response-hours m) :closed? (:case/closed m)}))

(def ^:private case-pull
  [:case/id :case/account-id :case/status :case/assigned-agent-id
   :case/committed-response-hours :case/closed])

(defn- kb-article->tx [{:keys [id title status embargoed?]}]
  {:kb-article/id id :kb-article/title title
   :kb-article/status status :kb-article/embargoed (boolean embargoed?)})

(defn- pull->kb-article [m]
  (when (:kb-article/id m)
    {:id (:kb-article/id m) :title (:kb-article/title m)
     :status (:kb-article/status m) :embargoed? (:kb-article/embargoed m)}))

(def ^:private kb-article-pull
  [:kb-article/id :kb-article/title :kb-article/status :kb-article/embargoed])

(defrecord DatomicStore [conn]
  Store
  (svc-agent [_ id] (pull->agent (d/pull (d/db conn) agent-pull [:agent/id id])))
  (all-agents [_]
    (->> (d/q '[:find [?id ...] :where [?e :agent/id ?id]] (d/db conn))
         (map #(pull->agent (d/pull (d/db conn) agent-pull [:agent/id %])))
         (sort-by :id)))
  (account [_ id] (pull->account (d/pull (d/db conn) account-pull [:account/id id])))
  (case-record [_ id] (pull->case (d/pull (d/db conn) case-pull [:case/id id])))
  (all-cases [_]
    (->> (d/q '[:find [?id ...] :where [?e :case/id ?id]] (d/db conn))
         (map #(pull->case (d/pull (d/db conn) case-pull [:case/id %])))
         (sort-by :id)))
  (kb-article [_ id] (pull->kb-article (d/pull (d/db conn) kb-article-pull [:kb-article/id id])))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (commit-record! [s {:keys [effect path value]}]
    (case effect
      :case-status-upsert
      (d/transact! conn [(case->tx (merge (case-record s (:case-id value))
                                           {:status (:to-status value)
                                            :assigned-agent-id (:assigned-agent-id value)
                                            :committed-response-hours (:committed-response-hours value)
                                            :closed? (boolean (:closed? value))}))])
      :kb-article-publish
      (d/transact! conn [(kb-article->tx (merge (kb-article s (:article-id value)) {:status :published}))])
      :correction-apply
      (d/transact! conn [(case->tx (merge (case-record s (first path)) (:patch value)))])
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-agents [s ags]
    (when (seq ags) (d/transact! conn (mapv agent->tx (vals ags)))) s)
  (with-accounts [s accts]
    (when (seq accts) (d/transact! conn (mapv account->tx (vals accts)))) s)
  (with-cases [s cs]
    (when (seq cs) (d/transact! conn (mapv case->tx (vals cs)))) s)
  (with-kb-articles [s arts]
    (when (seq arts) (d/transact! conn (mapv kb-article->tx (vals arts)))) s))

(defn datomic-store
  ([] (datomic-store {}))
  ([{:keys [agents accounts cases kb-articles]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (-> s (with-agents agents) (with-accounts accounts)
         (with-cases cases) (with-kb-articles kb-articles)))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo data — proves protocol parity."
  []
  (datomic-store (demo-data)))

;; ───────────────────────── ledger formatting ─────────────────────────

(defn ledger-line
  [{:keys [op actor subject disposition basis]}]
  (str/join " · "
            [(name disposition)
             (str "op=" op)
             (str "actor=" actor)
             (str "subject=" subject)
             (str "basis=" (pr-str basis))]))
