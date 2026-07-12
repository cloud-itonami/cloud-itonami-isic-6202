# Contributing

`cloud-itonami-isic-6202` accepts contributions to the OSS actor, governor
tests, documentation, examples and open business blueprint.

## Development

```bash
clojure -M:dev:test
clojure -M:lint
```

Keep changes small and include tests for governor, audit, store or
disclosure behavior.

## Rules

- Do not commit real customer/account data, real agent PII, or credentials.
- Keep production case-status transitions, KB publishes and disclosures
  behind ServiceGovernor.
- Treat every new case status or SLA tier as high-risk: add tests for
  sla-tier-gate, case-status-sequence-gate, double-close-gate,
  licensed-disclosure, confidence floor, kb-embargo-gate,
  refund-commitment-gate, and audit logging.
- Never fabricate an SLA/subscription tier or a source-provenance class
  to expand apparent coverage.
- Document any new business-model or operator assumption in `docs/`.

## Pull Requests

PRs should describe:

- what behavior changed
- which governor invariant is affected
- how it was tested
- whether operator or certification docs need updates
