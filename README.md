# cloud-itonami-isic-6202

Open Business Blueprint for **ISIC Rev.4 6202**: computer consultancy and
computer facilities management activities, narrowed to a **CRM/
subscription-integrated customer-service-hub SaaS platform** business —
the HubSpot Service Hub/Salesforce Service Cloud class of business —
published as an OSS business that any qualified operator can fork,
deploy, run, improve and sell.

Support cases move through a governed lifecycle and get their SLA
response-time commitments checked against the account's paying
subscription tier before anything commits. Built on this workspace's
[`langgraph`](https://github.com/kotoba-lang/langgraph) StateGraph
runtime and [`kotoba-lang/crm`](https://github.com/kotoba-lang/crm)'s
technical commons — the same actor pattern as
[`cloud-itonami-isic-5820`](https://github.com/cloud-itonami/cloud-itonami-isic-5820)
(this actor's direct sibling, which names this build in its own
sibling-actor roadmap) and
[`cloud-itonami-isic-6209`](https://github.com/cloud-itonami/cloud-itonami-isic-6209)
(a distinct sibling — IT-helpdesk ticket routing with no CRM/
subscription linkage at all; see `docs/business-model.md` for the
differentiation).

> **Why an actor layer at all?** A SupportOps-LLM is great at
> normalizing incoming case activity and drafting status-transition
> proposals — but it has **no notion of SLA-entitlement scope,
> case-lifecycle sequence validity, embargoed-content risk, or a
> subscriber's disclosure entitlement**. Letting it commit directly
> invites a response-time promise faster than the account's plan
> entitles, a case status being skipped or reopened after close, an
> embargoed knowledge-base article being auto-published, or a
> refund/credit-implying commitment slipping through unreviewed. This
> project seals the SupportOps-LLM into a single node and wraps it with
> an independent **ServiceGovernor**, a human **review workflow**, and
> an immutable **audit ledger**.

## Scope (deliberately narrow — read this before anything else)

This actor governs **support-case status transitions, SLA-entitlement
enforcement tied to a paying account's subscription tier, and
knowledge-base publish gating**. It never provides sales-pipeline/
subscription-commerce governance (`cloud-itonami-isic-5820`) or
IT-technician ticket routing by access-tier/certification
(`cloud-itonami-isic-6209`) — those are separate sibling
`cloud-itonami-*` actors. Marketing automation (campaigns, email
sequences, lead scoring) is a further planned sibling, not folded into
this one (see `docs/business-model.md`'s roadmap section).

## The core contract

```
request + injected role/agent/phase context
        │
        ▼
   ┌─────────────────┐  proposal      ┌──────────────────────────┐
   │ SupportOps-LLM   │ ─────────────▶ │ ServiceGovernor            │  (independent system)
   │ (sealed)         │  draft +       │  sla-tier ·                │
   └─────────────────┘  source         │  case-status-sequence ·    │
                                        │  kb-embargo · refund-risk  │
                                        └──────────────────────────┘
                                              │
                                   commit / disclose only if allowed
                                              ▼
                                    append-only audit ledger
```

**Single invariant**: SupportOps-LLM never transitions, publishes,
discloses, or resolves a dispute the ServiceGovernor would reject.

## Run

```bash
clojure -M:dev:test
clojure -M:dev:run
```

## Documentation

- `docs/business-model.md` — the OSS open-business blueprint
- `docs/DESIGN.md` — actor architecture (Japanese)
- `docs/operator-guide.md` — fork/run/production checklist
- `docs/adr/0001-architecture.md` — the authoritative architecture record

## License

AGPL-3.0 — see `LICENSE`.
