# HTTP API (`src/svcdesk/http.clj`)

This is the first HTTP service layer over the `cloud-itonami-isic-6202`
actor. It is a **thin adapter**: it does not reimplement any governance
logic. Every governed decision is produced by the exact same
`svcdesk.operation`/`svcdesk.policy` code the library entry points
(`clojure -M:dev:run`, the test suite) already use.

## Honest scope (read this first)

This is a **real, network-callable service** — not a mock, not a demo
stub. It is also **not yet production-hardened**:

- **Single process, single tenant.** One JVM process, one in-memory or
  disk-durable `Store` injected at startup. There is no multi-tenant
  isolation — every caller with a valid token shares the same store.
- **No TLS termination.** This binds plain HTTP. Put a reverse proxy
  (nginx, Caddy, Cloudflare, an ALB, …) in front for HTTPS/TLS — that is
  explicitly the reverse proxy's job, not this process's.
- **No rate limiting.** Any caller with a valid bearer token can call
  `/propose` as fast as they like.
- **No request logging/observability** beyond whatever the operator adds
  externally (this file adds none beyond what `httpkit`/the JVM already
  emit on stdout/stderr).
- **No HTTP endpoint for human approval/rejection of an escalated
  proposal.** `svcdesk.operation`'s graph has a real human-in-the-loop
  interrupt (`:request-approval`) for low-confidence/kb-embargo/refund-
  commitment/dispute cases; `POST /propose` surfaces that as `202
  escalated` with a `thread-id`, but there is currently no HTTP route to
  submit the approval/rejection that would resume that graph run. That
  resume path today only exists in-process (`langgraph.graph/run*` with
  `:resume? true`) — a follow-up task, not part of this first HTTP layer.
- **No book-wide/cross-record aggregate view.** Unlike sibling actor
  `cloud-itonami-isic-5820`'s `GET /dashboard` (a `crm.dashboard`-backed
  pipeline funnel + revenue rollup), this actor's domain has no such
  aggregate: `svcdesk.report/render-case` is a per-case, governor-
  approved-columns-only disclosure render, already reachable through
  `POST /propose`'s `:disclosure/query` op. There is deliberately no
  separate route for it here — inventing a book-wide feature this
  actor's domain does not implement would not be genuine parity.

If you need multi-tenant isolation, TLS, rate limiting, or the approval
resume endpoint, that is future work — do not assume this service
already has it.

## Auth

All endpoints except `GET /` and `GET /health` require:

```
Authorization: Bearer <token>
```

The token is whatever value the server was started with — see
"Running the server" below. **Auth is fail-closed**:

- `svcdesk.http/start-server!` throws (refuses to start) if given a
  nil/blank token.
- `clojure -M:serve` (`svcdesk.http/-main`) reads `$ISIC6202_API_TOKEN` at
  startup; if it is unset or blank, it prints a fatal error to stderr
  and exits `1` **without starting the server at all**. There is no
  "runs with auth disabled" fallback anywhere in this code.
- Every request to a protected endpoint that doesn't present the exact
  matching bearer token gets `401 {"error": "unauthorized"}`.
- The token match itself is a **constant-time comparison**
  (`java.security.MessageDigest/isEqual` over UTF-8 bytes, not `=`) —
  the same discipline sibling actors `cloud-itonami-isic-5820`/
  `cloud-itonami-isic-6201` already apply (ADR-2607124600).

There is no built-in default/fallback token anywhere in `svcdesk.http` —
you must supply one.

## Running the server

```bash
ISIC6202_API_TOKEN=<your-token> clojure -M:serve
# optional: ISIC6202_HTTP_PORT=9000 (default 8080)
# optional: ISIC6202_STORE_FILE=/path/to/db.edn  -- see "Persistence" below
```

If `$ISIC6202_STORE_FILE` is **unset**, `-main` starts the server against
a fresh `svcdesk.store/seed-db` — the same small fictitious demo dataset
`svcdesk.sim` uses (agents `agent-100`/`agent-200`, accounts
`acct-acme`/`acct-basic`, three cases, two kb-articles) — **and prints a
WARNING to stderr** that all state will be lost when the process exits.
There is no silent/default path into that mode.

### Running via Docker

See the README's **[Running via Docker](../README.md#running-via-docker)**
section for the exact `docker build`/`docker run` commands. Env vars
above are read from the container's environment unchanged — nothing is
baked into the image.

### Persistence

`svcdesk.http/-main` picks its `Store` backend from
`$ISIC6202_STORE_FILE`:

- **Set** (e.g. `ISIC6202_STORE_FILE=/var/lib/isic6202/db.edn`) — runs
  against `svcdesk.file-store/FileStore`: a full EDN snapshot of every
  agent/account/case/kb-article and the audit ledger is written to that
  path after every mutating call (write-then-rename, so a crash
  mid-write can't leave a truncated snapshot), and loaded back from that
  path the next time the process starts. See `svcdesk.file-store`'s ns
  docstring for what this backend is NOT (not multi-writer-safe — one
  path, one process at a time; no query engine; no transaction history).
- **Unset** — runs against `svcdesk.store/seed-db` (ephemeral, in-memory,
  discarded on exit) and prints a stderr WARNING every time.

**`svcdesk.store/datomic-store`/`DatomicStore` is deliberately NOT wired
into `-main` at all** — its constructor
(`(->DatomicStore (langchain.db/create-conn schema))`) builds a plain
`(atom {:db ... :log []})` via `langchain.db` (a pure, dependency-free,
**in-process** EAV emulation — see that ns's docstring) — there is no
connection URI, no socket, no file, nothing that outlives the JVM heap.
It is Datomic-API-*shaped* (which is what makes
`test/svcdesk/store_contract_test.clj`'s `MemStore ≡ DatomicStore`
parity test meaningful for a *future* backend swap), not Datomic-
*backed*. As shipped, selecting `DatomicStore` for `-main` would be
exactly as ephemeral as `seed-db` — just with a name that implies
otherwise — so this fix does not offer it as an `-main` option under any
env var name, to avoid exactly the "silently substitute an in-memory
store relabeled as persistent" trap (the same reasoning sibling actors
`crm.http`/`marketing.http` already document for their own
`DatomicStore`).

Making `DatomicStore` genuinely durable is real follow-up work, not done
here: `svcdesk.store` would need to accept an injected `:db-api` map (the
shape `langchain.db/api` already documents) instead of hardcoding calls
to `langchain.db` directly, pointed at either a real Datomic Local
process or a live kotoba-server pod via
`langchain.kotoba-db/kotoba-api` — both require infrastructure (a running
server, credentials) this sandboxed build environment does not have, so
that path was not attempted here rather than faked.

## Endpoints

### `GET /`

No auth. Info/discovery page (JSON, not HTML — this is a headless
service).

```bash
curl -s http://localhost:8080/
```

```json
{
  "actor": "cloud-itonami-isic-6202",
  "isic-code": "6202",
  "version": "0.1.0",
  "links": {
    "health": "/health",
    "propose": "/propose",
    "api-docs": "docs/api.md"
  }
}
```

### `GET /health`

No auth. Liveness + a cheap store-connectivity check (calls
`svcdesk.store/all-agents` against the injected store and reports
whether it threw).

```bash
curl -s http://localhost:8080/health
```

```json
{"status": "ok", "store": "reachable"}
```

Returns `503 {"status": "degraded", "store": "unreachable"}` if the
store call throws.

### `POST /propose`

Auth required. Runs a request through the **existing**
`svcdesk.operation/build` OperationActor graph exactly as `svcdesk.sim`/
the test suite already do: SupportOps-LLM (`svcdesk.llm`) drafts a
proposal -> ServiceGovernor (`svcdesk.policy/check`) censors it -> the
phase gate (`svcdesk.phase/gate`) applies rollout-phase restrictions ->
commit / hold / escalate. This endpoint reimplements none of that — it
is a JSON adapter over one `langgraph.graph/run*` call with a fresh
thread-id.

**Request body** — top-level fields map 1:1 onto `svcdesk.operation`'s
existing `request` map (see `svcdesk.llm`'s ns docstring / `svcdesk.sim`
for the canonical shapes this actually accepts), plus a nested
`"context"` for the caller's role/phase:

```json
{
  "op": "case/transition-status",
  "subject": "case-100",
  "case-id": "case-100",
  "to-status": "in-progress",
  "agent-id": "agent-100",
  "committed-response-hours": 48,
  "source": {"class": "case-management-log", "ref": "op1"},
  "context": {"actor-id": "agent-1", "actor-role": "agent", "phase": 3}
}
```

Field notes:

- `"op"` — one of `"case/transition-status"`, `"kb/publish-article"`,
  `"disclosure/query"`, `"dispute/request"` (namespaced — the `/` is
  significant, it becomes `:case/transition-status` etc.).
- `"to-status"`, `"disputed-field"`, `"claim"`, `"source".class`,
  `"context".actor-role` — coerced from JSON strings to Clojure
  keywords. Everything else (ids, hours, booleans, numbers) passes
  through as-is.
- `"context"` fields mirror what `svcdesk.operation`'s `context` map
  already expects: `actor-id` (string), `actor-role` (one of `"agent"`,
  `"support-manager"`, `"account-holder"`), `phase` (integer 0-3, see
  `svcdesk.phase`; omitted = `svcdesk.phase/default-phase` = `1`, the
  most conservative phase, same fail-closed default the library itself
  uses).

**Disclosure query example**:

```json
{
  "op": "disclosure/query",
  "subject": "acct-basic",
  "account-id": "acct-basic",
  "context": {"actor-id": "sub-1", "actor-role": "account-holder"}
}
```

**Dispute example**:

```json
{
  "op": "dispute/request",
  "subject": "case-100",
  "disputed-field": "status",
  "claim": "new",
  "context": {"actor-id": "mg-1", "actor-role": "support-manager"}
}
```

**Responses**:

- `200` — the graph ran to completion (`:done`). Body:
  - Committed: `{"decision": "committed", "op": .., "subject": .., "record": {...}}`
  - Held (a HARD governor violation, or phase-disabled): `{"decision": "held", "op": .., "subject": .., "violations": [{"rule": "...", "detail": "..."}], "confidence": 0.0-1.0}`
- `202` — the graph interrupted before human approval (SOFT/always-escalate: low confidence, kb-embargo-imminent, refund-commitment-imminent, any `dispute/request`, or a phase-approval gate): `{"decision": "escalated", "op": .., "subject": .., "thread-id": "...", "reason": "...", "confidence": .., "note": "..."}`. See "Honest scope" above — there is no HTTP endpoint yet to submit the approval for this `thread-id`.
- `400` — missing/invalid JSON body, or missing required `"op"`.
- `401` — missing/incorrect bearer token.
- `500` — unexpected error (includes the exception message; this is a bug if it happens for a documented request shape).

**curl examples**:

```bash
# Clean transition -> commit
curl -s -X POST http://localhost:8080/propose \
  -H "Authorization: Bearer $ISIC6202_API_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"op":"case/transition-status","subject":"case-100",
       "case-id":"case-100","to-status":"in-progress","agent-id":"agent-100",
       "committed-response-hours":48,
       "source":{"class":"case-management-log","ref":"op1"},
       "context":{"actor-id":"agent-1","actor-role":"agent","phase":3}}'

# SLA entitlement exceeded (acct-basic is :tier/basic, min 48h) -> held
curl -s -X POST http://localhost:8080/propose \
  -H "Authorization: Bearer $ISIC6202_API_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"op":"case/transition-status","subject":"case-300",
       "case-id":"case-300","to-status":"resolved","agent-id":"agent-200",
       "committed-response-hours":4,
       "source":{"class":"case-management-log","ref":"op3"},
       "context":{"actor-id":"agent-1","actor-role":"agent","phase":3}}'
```

## Testing

`test/svcdesk/http_test.clj` starts the real `svcdesk.http` server on an
ephemeral port (`:port 0`) inside the test JVM via `start-server!`,
makes real HTTP requests against it with `java.net.http` (no mocked
handler shortcut), and stops the server in teardown. See that file for
the exact scenarios covered (health with no auth; `/propose` without a
token -> 401; wrong-length/same-length-wrong tokens -> 401; a clean
transition reusing `svcdesk.sim`'s own op1 scenario -> committed; an
SLA-entitlement violation reusing op3 -> held with violations).

## Real-model SupportOps-LLM advisor (`src/svcdesk/llm_realmodel.clj`)

**Honest gap, and what closes it.** `src/svcdesk/llm.cljc`'s
SupportOps-LLM advisor is a SEALED, deterministic mock
(`svcdesk.llm/mock-advisor` / `svcdesk.llm/infer`) — it never calls a
real language model. That was a genuine, known gap toward real
production operation, and the exact same gap sibling actors
`cloud-itonami-isic-5820` and `cloud-itonami-isic-6201` already closed
for their own domains. `svcdesk.llm-realmodel` adds the ADAPTER so an
operator who supplies real model API credentials gets a genuinely wired
real-model advisor — **it does not itself supply those credentials, and
no real model call has been exercised anywhere in this build** (this
sandbox has none of `ANTHROPIC_API_KEY`/`OPENAI_API_KEY`/etc.). See
"What is verified vs. unverified" below before relying on this in
production.

`svcdesk.llm.cljc` already had exactly the seam this needed:
`svcdesk.llm/llm-advisor` wraps ANY `langchain.model/ChatModel` in the
same `svcdesk.llm/Advisor` protocol (`-advise`) `svcdesk.operation/
build`'s `:advise` node calls — same proposal shape in, same proposal
shape out. This file does not change that graph-facing contract at all;
it only supplies a second, real `ChatModel` implementation
(`langchain.model/openai-model`/`anthropic-model`, both already
generic/already-tested upstream in `kotoba-lang/langchain`) for
`llm-advisor` to wrap.

### Env vars (mirrors this org's own `ITO_MODEL_*` convention)

This repo follows the SAME convention `orgs/gftdcojp/cloud-itonami`'s
`cloud_itonami.runtime` namespace already established for this lineage —
adapted with an `ISIC6202_`-prefix so sibling `cloud-itonami-isic-*`
actors on the same host/CI never collide on one another's model config:

| Env var | Required? | Meaning |
|---|---|---|
| `ISIC6202_MODEL_API_KEY` | **the sole trigger** | If unset/blank, `svcdesk.http` runs the sealed mock advisor (unchanged default behavior). If set and non-blank, `svcdesk.http` runs the real-model advisor instead. |
| `ISIC6202_MODEL_PROVIDER` | optional (default `openai`) | `openai` \| `anthropic` \| `openclaw` (any OpenAI-compatible endpoint at a custom URL — self-hosted gateway, Ollama, vLLM, etc.) |
| `ISIC6202_MODEL_URL` | required for `openclaw`, optional override for openai/anthropic | Chat-completions endpoint URL. `openai`/`anthropic` already have a public default hardcoded in `langchain.model`. |
| `ISIC6202_MODEL` | optional | Model name (default `gpt-4o-mini` for openai/openclaw, `claude-opus-4-8` for anthropic). |

```bash
ISIC6202_API_TOKEN=<token> \
ISIC6202_MODEL_API_KEY=<real key> \
ISIC6202_MODEL_PROVIDER=openai \
ISIC6202_MODEL=gpt-4o-mini \
  clojure -M:serve
```

### Startup log / `preflight`

`svcdesk.http/-main`/`start-server!` always prints which advisor mode it
picked (`resolve-advisor!`) plus `svcdesk.llm-realmodel/preflight`'s
report — **the same fail-visible discipline `warn-ephemeral-store!`
already established for storage**. `preflight` never attempts a live
network call and never prints the API key value (only `:api-key?`, a
boolean):

```clojure
(svcdesk.llm-realmodel/preflight {:provider "openclaw"})
;=> {:provider :openclaw, :url nil, :api-key? false, :model "gpt-4o-mini",
;    :ok? false, :missing [:ISIC6202_MODEL_URL :ISIC6202_MODEL_API_KEY]}
```

### What is verified vs. genuinely unverified

- **Verified**: `preflight`'s missing/present reporting across the full
  permutation matrix (openai/anthropic/openclaw, present/absent url,
  present/absent key, unknown provider, blank-string env values) — see
  `test/svcdesk/llm_realmodel_test.clj`'s `preflight-*` tests.
- **Verified**: the exact JSON request this adapter sends (method,
  bearer header, model field, message shape) and its parsing of a
  well-formed OpenAI-compatible response — against a **real local
  `org.httpkit.server` stub** bound to an ephemeral port (a real HTTP
  round-trip over a real socket, not an in-process function fake) —
  including a full round-trip through `svcdesk.llm/llm-advisor` ->
  `svcdesk.llm/parse-proposal` into the exact proposal shape
  `svcdesk.operation/build` expects, and the fallback path for a
  non-EDN-parseable model response.
- **Genuinely UNVERIFIED, and cannot be verified in this sandbox**:
  whether any ACTUAL target model API (OpenAI, Anthropic, or a real
  OpenAI-compatible gateway) accepts this exact request shape, or how it
  actually behaves — there are no model API credentials available here,
  and fabricating a fake endpoint would not prove anything about a real
  one. An operator who sets `ISIC6202_MODEL_API_KEY` for real gets a
  genuinely wired adapter, but its real-call behavior is unverified
  until they exercise it themselves.
