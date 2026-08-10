# Open Business Blueprint: cloud-itonami-isic-6202

This repository publishes an OSS business model for operating a
CRM/subscription-integrated **customer-service hub** SaaS platform on
itonami.cloud — the HubSpot Service Hub/Salesforce Service Cloud class
of business.

## Classification

- Repository name: `cloud-itonami-isic-6202`
- Primary classification: ISIC Rev.4 6202 (Computer consultancy and
  computer facilities management activities), narrowed to a specific
  business model: **hosting and operating a multi-tenant support-case +
  SLA-entitlement + knowledge-base management platform as a facility for
  client businesses** — the official ISIC 6202 explanatory note
  (UNSD classification detail, code 6202) includes "provision of on-site
  management and operation of clients' computer systems and/or data
  processing facilities, **plus related support services**" — this actor
  IS that "related support services" facility, delivered as a governed,
  subscription-aware SaaS product rather than bespoke on-site consulting.
  Not computer programming in general, not generic IT consultancy, and
  not IT-helpdesk/technician ticket-routing (see `cloud-itonami-isic-6209`,
  a distinct sibling — differentiated below).
- Served domain: support-case lifecycle management, SLA-entitlement
  enforcement tied to a paying account's subscription tier, governed
  knowledge-base publishing, governed disclosure, dispute handling —
  never sales-pipeline/subscription-commerce (`cloud-itonami-isic-5820`),
  never IT-technician ticket routing by access-tier/certification
  (`cloud-itonami-isic-6209`), never call-centre staffing/BPO
  (`cloud-itonami-isic-8220`).

## Distinct from siblings (read this before assuming overlap)

- **`cloud-itonami-isic-5820`** (commercial CRM/subscription-commerce,
  Salesforce/HubSpot-class) explicitly names this actor as its own
  "customer-service hub" sibling-roadmap item in its
  `docs/business-model.md`: *"Customer-service hub (support cases, SLAs,
  knowledge base) — HubSpot Service Hub/Salesforce Service Cloud
  equivalent."* This build is that sibling.
- **`cloud-itonami-isic-6209`** (IT managed-services/helpdesk
  ticket-routing, TicketRouter-LLM ⊣ TicketGovernor) routes an incident
  ticket to a contracted technician by access-tier/certification. It has
  **no CRM/subscription-account linkage at all** — no account entity, no
  subscription tier, no billing-system-of-record integration. This actor
  is the opposite emphasis: a support case is always tied to a paying
  account's subscription tier, and the SLA a case may be promised is a
  function of that account's entitlement, never of a technician's
  certification. The two actors do not overlap in schema, in governor
  gates, or in the operator persona they serve (MSP dispatch desk vs.
  SaaS vendor's own customer-support hub).
- **`cloud-itonami-isic-8220`** (activities of call centres) is a
  services/staffing BPO business — it registers/dispatches human agents
  and produces quality-assurance/compliance records for a client's
  inbound/outbound call campaigns. It is not a software product at all;
  this actor is a SaaS platform a business would buy/self-host to run
  its OWN support desk, never a staffing agency.

## Customer

- SaaS vendors and SMB/mid-market service businesses needing a governed,
  audit-ready customer-service hub without building SLA-entitlement
  enforcement logic themselves
- SaaS vendors needing to enforce that a support-case commitment cannot
  itself grant an SLA response time beyond what billing has actually
  provisioned
- other `cloud-itonami-{ISIC}` blueprint operators needing support-case
  governance as a licensed capability

## Problem

Commercial customer-service platforms (Zendesk, HubSpot Service Hub,
Salesforce Service Cloud) route SLA commitments and case-status updates
through configurable-but-optional workflow rules, with no STRUCTURAL
guarantee against an agent promising a response time faster than the
customer's plan entitles, a case status being skipped or reopened after
close, an already-closed case being re-transitioned, an embargoed
knowledge-base article being auto-published, or a refund/credit-implying
commitment slipping through without human review. This platform seals
the SupportOps-LLM into a single node and wraps it with an independent
ServiceGovernor, a human review workflow, and an immutable audit
ledger — the same discipline `cloud-itonami-isic-5820` and
`cloud-itonami-isic-6209` apply to their own domains.

## Revenue Model

- Per-agent-seat subscription (mirrors the platform's own product: a
  customer-service-hub business licenses seats to its own customers)
- Certification/audit fee for itonami.cloud operator certification
- Optional managed-hosting fee for operators who do not self-host

| Package | Customer | Price shape |
|---|---|---|
| Self-host | operator runs their own instance | AGPL-3.0-or-later, no fee |
| Managed Starter | one tenant, SaaS vendor's own support desk (~10 agents) | ¥40,000/月 flat |

**Market-anchored (2026-08-10)**: benchmarked against 5 real
customer-service-hub products. **Only 3 of the 5 publish real numbers, and
all three are non-Japanese.** **Zendesk Suite**: 「$55 エージェント/月（年払
い）」 (Team), 「$115 エージェント/月（年払い）」 (Professional); Enterprise is
not published and routes to sales (<https://www.zendesk.co.jp/pricing/>).
**HubSpot Service Hub**: Starter「最低利用料金：￥840／月／シート」,
Professional「￥10,800／月／シート」, Enterprise「￥18,000／月／シート」
(<https://www.hubspot.jp/pricing/service>). **Freshdesk**: Growth
`$19/agent/month, billed annually`, Pro `$55/agent/month`, Enterprise
`$89/agent/month` (<https://www.freshworks.com/freshdesk/pricing/>). The two
Japanese products **publish nothing usable**: **メールディーラー**'s pricing
route carries no figures and redirects to a price-inquiry form
(<https://www.maildealer.jp/plan/>), and **Re:lation** publishes plan
structure and seat/storage allowances (フリー 1名/100MB、スターター 1名/10GB、
ビジネス 5名/20GB、プロ 10名/30GB、エンタープライズ 個別) but no yen figures,
stating「※月額費用はご利用のユーザ数やストレージによって変わります」
(<https://ingage.jp/relation/pricing/>). Third-party aggregator figures for
those two exist but are **not first-party and are therefore not used as an
anchor here**.

Converting at ~¥150/$ for the assumed customer (one tenant, a SaaS vendor's
own support desk at ~10 agent seats), the published band is ¥28,500/月
(Freshdesk Growth ×10) to ¥172,500/月 (Zendesk Suite Professional ×10), with
Zendesk Team and Freshdesk Pro both landing at ¥82,500/月 and HubSpot Service
Professional at ¥108,000/月. **¥40,000/月 sits near the bottom of that
measured band**, about 1.4× the floor and well under half of Zendesk Team,
because this actor ships no ticketing UI, no omnichannel or live chat, no
agent-transfer routing, no CSAT engine and no billing logic — it is the
SLA-entitlement, case-status-sequence, KB-embargo and refund-risk **gate**
that sits on top of whatever desk the vendor already runs.

**The flat shape is itself the argument, not a packaging convenience.** Every
one of the three disclosing comparators charges per agent seat. The
ServiceGovernor's work does not scale with agent headcount — it scales with
case volume, because the governor evaluates cases, not people. Charging per
seat would therefore decouple price from the quantity of work actually
performed in both directions: a ten-agent desk handling few cases would
overpay, and a three-agent desk handling many would underpay for the same
governance load. A flat monthly tenant fee keeps price aligned with what the
governor actually does. The one property no comparator has structurally is
the reason the fee is non-zero next to a ¥28,500 full desk: an agent cannot
promise a response time faster than the account's subscription tier has
actually provisioned, because the check is an independent governor rather
than a configurable-but-optional workflow rule.

**Subscribe (2026-08-10)**: a live Stripe Payment Link for the Managed
Starter tier (¥40,000/月 flat) is available now —
[**subscribe to Managed Service Desk Ops — Starter**](https://buy.stripe.com/4gMfZieuX9xu4ze2DEeEo0g).
This is a no-code Stripe-hosted checkout; nothing in this repo's actor code
changed. After subscribing, contact gftdcojp to arrange managed-tenant setup
(manual fulfillment today, no automated onboarding yet). **No SaaS vendor or
service business has claimed or subscribed to this tier yet — this is a live,
working checkout with zero paid tenants, not a claim of existing revenue.**

## Honest scope (R0)

- Support-case lifecycle (4-status linear + 1 exit) and SLA-entitlement
  governance only.
- SLA governance is a single response-time-commitment check (fastest
  hours a subscription tier entitles) — no multi-metric SLA (resolution
  time, uptime credits, priority-tier matrices), no per-agent authority
  tiers, no automated breach-credit calculation.
- Knowledge-base governance is publish-gating on a single `:embargoed?`
  boolean — no versioning, no approval workflow beyond the embargo
  check, no full-text content moderation.
- 3 subscription/SLA tiers, 3 evidence source classes, 4 case statuses —
  extend only by adding real, documented tiers/classes/statuses.
- No omnichannel chat, no live-agent transfer routing, no CSAT survey
  engine, no billing/revenue-recognition logic (see
  `cloud-itonami-isic-5820`'s `kotoba.crm.revrec` for that, out of scope
  here), no technician access-tier/certification routing (see
  `cloud-itonami-isic-6209` for that, out of scope here).

## Technical commons reused

`kotoba.crm.pipeline` (generic ordered-stage transition validator,
`kotoba-lang/crm`) drives `case-status-sequence-gate` — a support case's
`:new → :in-progress → :resolved → :closed` lifecycle (plus the
`:cancelled` exit) is directly analogous to `cloud-itonami-isic-5820`'s
sales-pipeline stage shape, so this actor depends on the shared
technical commons rather than re-deriving stage-transition logic.
`kotoba.crm.revrec` and `kotoba.crm.leadscore` are NOT used — this actor
has no revenue-recognition or lead-scoring responsibility.

## Sibling-actor roadmap

Per `cloud-itonami-isic-5820`'s own roadmap, the marketing-automation hub
(campaigns, email sequences, lead scoring — HubSpot Marketing Hub/
Salesforce Marketing Cloud equivalent) is the third sibling in this
CRM-vertical family, tracked as a separate `cloud-itonami-*` actor
sharing `kotoba-lang/crm`'s technical commons. This build (the
customer-service hub) completes the second of the two siblings named in
`cloud-itonami-isic-5820`'s roadmap.
