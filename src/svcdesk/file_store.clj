(ns svcdesk.file-store
  "A genuinely disk-durable `svcdesk.store/Store` implementation: a full EDN
  snapshot written to a local file on every mutating call, reloaded from
  that file on construction. State survives a process restart — the same
  contract `cloud-itonami-isic-5820`'s `crm.file-store` and
  `cloud-itonami-isic-6201`'s `marketing.file-store` already established
  for this actor lineage (`test/svcdesk/file_store_test.clj` covers the
  same contract at the Store level, in-process, for fast regression
  coverage — mirroring those sibling suites).

  JVM-only (`.clj`, not `.cljc`) for the same reason `svcdesk.http` is: file
  I/O (`clojure.java.io`, `java.io.File`) has no portable equivalent at
  the kotoba-wasm/clojurewasm/cljs/nbb tier this fleet prefers, and this
  namespace is infrastructure glue over the already-portable
  `svcdesk.store/Store` protocol + `MemStore` implementation, not a
  reimplementation of any governance/domain logic.

  ## Why this exists instead of wiring `svcdesk.store/datomic-store`

  `svcdesk.store/DatomicStore` (`svcdesk.store/datomic-store`) is NOT
  durable across a process restart despite its name, as implemented in
  this repo today: its constructor is `(->DatomicStore (langchain.db/
  create-conn schema))` — `langchain.db/create-conn` returns a plain
  `(atom {:db ... :log []})`; there is no connection URI, no socket, no
  file, nothing that outlives the JVM heap. It is Datomic-API-*shaped*
  (via `langchain.db`, a pure in-process EAV emulation — see that ns's
  docstring), not Datomic-*backed*. `test/svcdesk/store_contract_test.clj`
  proves `MemStore` and `DatomicStore` are read/write-equivalent, which is
  true and valuable for a future backend swap — but neither one persists
  past the process, so picking `DatomicStore` for `-main` would not have
  fixed the honest-scope gap this file exists to close.

  Making `DatomicStore` genuinely durable would need two things this
  sandbox does not have: (1) `svcdesk.store` refactored so `DatomicStore`
  talks to an injected `:db-api` map (`langchain.db/api`'s own shape,
  per that ns's docstring) instead of hardcoding calls to `langchain.db`
  directly, and (2) a live Datomic Local process or a reachable
  kotoba-server pod (`langchain.kotoba-db/kotoba-api`) to point that
  `:db-api` at — the latter needs a running server + credentials neither
  present nor stand-up-able here. Rather than fake either of those (or
  quietly wire `DatomicStore` under a durability-implying env var name
  when it provides none), this file takes the path that IS honestly
  achievable and verifiable in this sandbox: real bytes on real disk.

  ## What this is NOT

  - NOT multi-writer-safe: two `-main` processes must never point at the
    same `path` concurrently — there is no file lock, no CAS, no
    coordination. Single-process, single-writer only (same single-process
    scope `docs/api.md` already documents for this HTTP layer generally).
  - NOT a query engine, NOT transactional history, NOT `as-of`/audit-log
    replay of the snapshot itself (the domain-level audit ledger
    `svcdesk.store/ledger` is unaffected — it round-trips through the
    same snapshot like every other field).
  - NOT crash-atomic against OS/disk failure beyond a single
    write-then-rename per mutation (see `persist!`) — good enough for a
    single operator's dev/small-deployment durability, not a
    replacement for a real transactional database."
  (:require [clojure.edn :as edn]
            [svcdesk.store :as store])
  (:import (java.io File)))

(defn- persist!
  "Writes `db` (the full in-memory map: :agents :accounts :cases
  :kb-articles :ledger) as one EDN snapshot to `path`. Writes to a
  sibling `.tmp` file first and renames it over `path` so a crash
  mid-write can never leave `path` holding a truncated snapshot — the
  previous good snapshot stays live at `path` until the new one has
  fully landed on disk."
  [path db]
  (let [tmp (File. (str path ".tmp"))]
    (spit tmp (pr-str db))
    (.renameTo tmp (File. (str path)))))

(defn- load-or-seed!
  "Loads `path`'s EDN snapshot if it exists; otherwise seeds it with
  `svcdesk.store/demo-data` (the same fictitious dataset
  `svcdesk.store/seed-db` uses) and writes that as the first snapshot, so
  a brand-new path behaves like a fresh `seed-db` on first boot but is
  durable from then on."
  [path]
  (let [f (File. (str path))]
    (if (.exists f)
      (edn/read-string (slurp f))
      (let [db (assoc (store/demo-data) :ledger [])]
        (persist! path db)
        db))))

(defrecord FileStore [mem path]
  store/Store
  (svc-agent [_ id] (store/svc-agent mem id))
  (all-agents [_] (store/all-agents mem))
  (account [_ id] (store/account mem id))
  (case-record [_ id] (store/case-record mem id))
  (all-cases [_] (store/all-cases mem))
  (kb-article [_ id] (store/kb-article mem id))
  (ledger [_] (store/ledger mem))
  (commit-record! [s record]
    (store/commit-record! mem record)
    (persist! path @(:a mem))
    s)
  (append-ledger! [_ fact]
    (let [f (store/append-ledger! mem fact)]
      (persist! path @(:a mem))
      f))
  (with-agents [s ags]
    (store/with-agents mem ags) (persist! path @(:a mem)) s)
  (with-accounts [s accts]
    (store/with-accounts mem accts) (persist! path @(:a mem)) s)
  (with-cases [s cs]
    (store/with-cases mem cs) (persist! path @(:a mem)) s)
  (with-kb-articles [s arts]
    (store/with-kb-articles mem arts) (persist! path @(:a mem)) s))

(defn file-store!
  "Opens (or creates) a disk-durable `Store` at `path` (a plain filesystem
  path string). If `path` already holds a snapshot, loads it; otherwise
  seeds it with the same demo dataset `svcdesk.store/seed-db` uses and
  writes that as the first snapshot. Every mutating call (`commit-record!`,
  `append-ledger!`, `with-agents`/`with-accounts`/`with-cases`/
  `with-kb-articles`) persists a fresh full snapshot to `path` before
  returning — the next `file-store!` on the same `path` (e.g. after a
  process restart) picks up exactly that state."
  [path]
  (->FileStore (store/->MemStore (atom (load-or-seed! path))) path))
