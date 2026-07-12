# Governance

`cloud-itonami-isic-6202` is an OSS open-business blueprint. Governance
covers both code and the operator model.

## Maintainers

Maintainers may merge changes that preserve these invariants:

- SupportOps-LLM cannot directly transition a case's status, publish a
  knowledge-base article, disclose account/case data, or resolve a
  dispute.
- ServiceGovernor remains independent of the advisor.
- hard governor violations (sla-tier-gate, case-status-sequence-gate,
  double-close-gate, source-provenance-gate, licensed-disclosure)
  cannot be overridden by human approval.
- a dispute request never auto-resolves, at any rollout phase.
- an embargoed knowledge-base article publish always reaches a human,
  regardless of confidence.
- a case commitment implying a refund/credit always reaches a human,
  regardless of confidence.
- every commit, hold and disclosure event is auditable.
- no schema field exists for discount-authority, sales-pipeline stage,
  or booked revenue — this actor is support-case + SLA-entitlement +
  knowledge-base governance only.

## Decision Records

Architecture decisions live in `docs/adr/`.

## Operator Governance

Anyone may fork and operate independently. itonami.cloud certification is a
separate trust mark and should require security, audit, support and
data-flow review.

Certified operators can lose certification for:

- bypassing governor checks
- committing a response-time SLA faster than an account's subscription
  tier has paid for
- publishing an embargoed knowledge-base article without human review
- disclosing account/case data to an uncontracted party
- misrepresenting an account's subscription/SLA tier
