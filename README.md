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

## Running as a service

`src/svcdesk/http.cljk` wraps this actor in a minimal, real HTTP service
(http-kit) — a governed, bearer-token-authenticated `POST /propose` +
`GET /health`/`GET /` — so it can actually run as a live process instead
of only being invoked as a library. **Auth is fail-closed**: the server
refuses to start at all without an explicit token.

```bash
ISIC6202_API_TOKEN=<your-token> clojure -M:serve   # port: $ISIC6202_HTTP_PORT, default 8080
# optional: ISIC6202_STORE_FILE=/path/to/db.edn -- disk-durable store (see docs/api.md's Persistence section)
```

**Persistence**: without `$ISIC6202_STORE_FILE`, `-main` runs against an
ephemeral in-memory store and prints a stderr WARNING — all state is
lost on restart. Set `ISIC6202_STORE_FILE` to a path to run against
`svcdesk.file-store/FileStore` instead, a disk-durable store. See
**[`docs/api.md`](docs/api.md)**'s Persistence section for the full
explanation, including why `svcdesk.store/DatomicStore` — despite its
name — is *not* wired in as a durable option (it is an in-process EAV
atom with no connection URI, exactly as ephemeral as the default store).

See **[`docs/api.md`](docs/api.md)** for the full endpoint reference
(request/response shapes, auth header, error codes, curl examples) and
its explicit honest-scope statement — this is a real network endpoint,
not yet production-hardened (single-process/single-tenant, no TLS
termination built in, no rate limiting, no book-wide aggregate view).

### Running via Docker

The `Dockerfile` is a multi-stage build: a builder stage (JDK + Clojure
CLI) clones this repo's `:local/root` sibling deps
(`kotoba-lang/{crm,langgraph,langchain}` — public repos; no uberjar/
`tools.build` alias exists in this repo, so the builder just resolves
the same classpath `clojure -M:dev:serve` would use) and records it to
a file; the runtime stage is a minimal `eclipse-temurin:21-jre-alpine`
image that replays that classpath with a plain `java` invocation as a
non-root user — no Clojure CLI, build tool, or network access needed to
run the container. All secrets/config
(`ISIC6202_API_TOKEN`/`ISIC6202_STORE_FILE`/`ISIC6202_MODEL_API_KEY`
etc.) are read from the container's environment only, never baked into
the image; `ISIC6202_API_TOKEN` has no default, matching `svcdesk.http`'s
own fail-closed contract. A `HEALTHCHECK` polls `GET /health`.

```bash
docker build -t cloud-itonami-isic-6202 .

mkdir -p /tmp/isic6202-store
docker run -d --name isic6202 \
  -p 18080:8080 \
  -e ISIC6202_API_TOKEN=test-token-abc123 \
  -e ISIC6202_STORE_FILE=/data/db.edn \
  -v /tmp/isic6202-store:/data \
  cloud-itonami-isic-6202

curl -s http://localhost:18080/health
# {"status":"ok","store":"reachable"}

docker stop isic6202 && docker rm isic6202
```

`ISIC6202_STORE_FILE` is bind-mounted so `svcdesk.file-store` snapshots
survive container restarts (see "Persistence" above) —
`/tmp/isic6202-store/db.edn` is written on the host after the first
request.

**Not included on purpose**: no `docker push`/registry step and no
cloud-deploy automation — CI (`.github/workflows/ci.yml`) only runs
`docker build .` as a build-breakage smoke test. Pushing to a registry
and deploying need registry credentials and a target-infrastructure
decision that are out of scope here.

### Real-model SupportOps-LLM advisor (optional)

By default the SupportOps-LLM advisor (`svcdesk.llm`) is a SEALED,
deterministic mock — no real language model is ever called.
`src/svcdesk/llm_realmodel.cljk` adds a real OpenAI-compatible/Anthropic
HTTP adapter, wired in via `svcdesk.http/resolve-advisor!`: set
`ISIC6202_MODEL_API_KEY` and the server uses it instead of the mock
(unset/blank = unchanged sealed-mock default).

```bash
ISIC6202_API_TOKEN=<token> ISIC6202_MODEL_API_KEY=<real key> clojure -M:serve
# optional: ISIC6202_MODEL_PROVIDER=openai|anthropic|openclaw (default openai)
# optional: ISIC6202_MODEL_URL (required for openclaw), ISIC6202_MODEL
```

**Honest caveat**: this adapter's real-call behavior against an actual
model API has never been exercised in this build (no credentials are
available in the environment it was built in) — it is verified only
against `preflight`'s reporting logic and a local `org.httpkit.server`
stub standing in for the model API. See **[`docs/api.md`](docs/api.md)**'s
"Real-model SupportOps-LLM advisor" section for exactly what is/isn't
proven.

## Documentation

- `docs/business-model.md` — the OSS open-business blueprint
- `docs/DESIGN.md` — actor architecture (Japanese)
- `docs/operator-guide.md` — fork/run/production checklist
- `docs/adr/0001-architecture.md` — the authoritative architecture record

## License

AGPL-3.0 — see `LICENSE`.
