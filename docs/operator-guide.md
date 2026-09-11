# Operator Guide

## 1. Fork and Run

```bash
git clone https://github.com/cloud-itonami/cloud-itonami-isic-6202
cd cloud-itonami-isic-6202
kbb -M:dev:test
kbb -M:dev:run
```

## 2. Production Checklist

- replace demo agents/accounts/cases/kb-articles with real, source-cited
  data
- configure Datomic Local, kotoba-server or an equivalent durable SSoT
- configure the LLM adapter through environment variables or secret manager
- define SLA/subscription tiers per account and RBAC rules for agents/
  support managers/account-holders
- run `kbb -M:dev:test` / `kbb -M:lint`
- verify audit-ledger export
- document backup/restore and incident response
- get written support/legal review on which response-time commitments
  are contractually defensible per subscription tier in your
  jurisdiction

## 3. Operator Responsibilities

- verify an account's subscription/SLA tier against the billing system
  of record before registering it in the store — this actor never
  invents entitlement, only enforces what's already provisioned
- secure infrastructure and tenant isolation
- human review workflow for kb-embargo, refund-commitment, and
  dispute-request operations
- data-retention policy
- security updates

The OSS project provides software and an operating blueprint. It does
not verify an account's billing status, an agent's employment status,
or which knowledge-base content is genuinely embargoed on the
operator's behalf.

## 4. Explicitly out of scope for R0

- Sales-pipeline stage transitions and subscription-commerce revenue
  recognition — see `cloud-itonami-isic-5820` (this actor's sibling).
- IT-technician ticket routing by access-tier/certification — see
  `cloud-itonami-isic-6209` (a distinct sibling, no CRM/subscription
  linkage).
- Marketing automation (campaigns, email sequences, lead scoring) —
  candidate for a further sibling `cloud-itonami-*` actor sharing
  `kotoba-lang/crm`'s pipeline commons, not yet built.
- Omnichannel chat, live-agent transfer routing, CSAT survey engine —
  not modeled.
- Multi-metric SLA (resolution time, uptime credits, priority-tier
  matrices), per-agent authority tiers, automated breach-credit
  calculation — only a single response-time-commitment check is
  modeled.
- Knowledge-base versioning, approval workflow beyond the embargo
  check, full-text content moderation.
