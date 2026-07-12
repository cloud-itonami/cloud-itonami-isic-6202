# Security Policy

This project handles account/subscription data, support-case content,
and knowledge-base content that may include embargoed/unreleased
product information. Treat vulnerabilities as potentially high impact
even when the demo data is synthetic — an over-entitled SLA commitment,
a leaked embargoed article, or an unreviewed refund commitment has
direct customer-trust and financial consequences.

## Do Not Disclose Publicly

Report privately before opening public issues for:

- credential exposure
- ServiceGovernor bypass (sla-tier-gate, case-status-sequence-gate,
  double-close-gate, licensed-disclosure)
- audit-ledger tampering
- over-disclosure beyond an account's subscription tier
- tenant/account isolation failures
- publishing an embargoed knowledge-base article without human review

## Reporting

Use GitHub private vulnerability reporting when available for the
repository. If that is unavailable, contact the repository maintainers
through the cloud-itonami organization before publishing details.

## Production Guidance

- Store secrets outside Git.
- Run governor tests before deployment.
- Export and review audit logs regularly.
- Use least privilege for agents and service accounts.
- Alert on any sla-tier-gate, kb-embargo-gate, or refund-commitment-gate
  HOLD/escalate spike.
