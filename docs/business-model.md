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
