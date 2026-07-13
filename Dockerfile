# syntax=docker/dockerfile:1
#
# cloud-itonami-isic-6202 -- container image for the svcdesk.http service.
#
# This repo has no uberjar/tools.build alias (see deps.edn): its own
# `clojure -M:serve` alias just runs `-m svcdesk.http` directly off the CLI
# classpath. So instead of inventing a build tool this repo doesn't
# have, the builder stage resolves the SAME classpath `clojure -M:dev:serve`
# would use (the `:dev` override pins `io.github.kotoba-lang/langchain` to
# the sibling checkout, exactly like this repo's own dev/test workflow),
# and the runtime stage just replays that resolved classpath with a plain
# `java` invocation -- no Clojure CLI, no git, no build tool, no network
# access needed at container run time. Mirrors this actor's siblings'
# Dockerfiles (`cloud-itonami-isic-5820`'s `crm.http`, `cloud-itonami-
# isic-6201`'s `marketing.http`) verbatim in shape, adapted for this
# repo's own entrypoint/env-var prefix.
#
# deps.edn's `:local/root` sibling deps (`../../kotoba-lang/{crm,langgraph}`)
# and the `:dev` alias's `../../kotoba-lang/langchain` override are NOT
# published to Maven/Clojars, so they must exist as real sibling checkouts
# on disk at resolve time -- the builder stage clones the public
# `kotoba-lang/{crm,langgraph,langchain}` repos into that exact relative
# layout (mirrors this org's own `orgs/<org>/<repo>` west layout).

FROM eclipse-temurin:21-jdk-alpine AS builder

RUN apk add --no-cache bash curl git tar \
    && curl -fsSL -o /tmp/linux-install.sh https://download.clojure.org/install/linux-install.sh \
    && chmod +x /tmp/linux-install.sh \
    && /tmp/linux-install.sh \
    && rm -f /tmp/linux-install.sh

# Sibling layout expected by deps.edn (relative to this repo's root):
#   <root>/orgs/cloud-itonami/cloud-itonami-isic-6202   (this repo)
#   <root>/orgs/kotoba-lang/crm
#   <root>/orgs/kotoba-lang/langgraph
#   <root>/orgs/kotoba-lang/langchain
WORKDIR /build/orgs/cloud-itonami/cloud-itonami-isic-6202
COPY . .

RUN git clone --depth 1 https://github.com/kotoba-lang/crm.git /build/orgs/kotoba-lang/crm \
    && git clone --depth 1 https://github.com/kotoba-lang/langgraph.git /build/orgs/kotoba-lang/langgraph \
    && git clone --depth 1 https://github.com/kotoba-lang/langchain.git /build/orgs/kotoba-lang/langchain

# Resolve deps (downloads jars/gitlibs into the image's Maven/gitlibs
# caches) and record the exact classpath svcdesk.http needs to run, so the
# runtime stage never has to invoke tools.deps / touch the network.
# NOTE: clj-opts like -Spath/-P must come BEFORE -M<aliases> in the
# command line, or they get parsed as `-main` args instead (and, worse,
# actually invoke -main -- svcdesk.http's fail-closed -main would exit 1
# here with no ISIC6202_API_TOKEN set, exactly as it did the first time
# this same mistake was made in `cloud-itonami-isic-5820`'s Dockerfile).
RUN clojure -Spath -M:dev:serve > /build/classpath.txt

# ---------------------------------------------------------------------

FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S isic6202 && adduser -S isic6202 -G isic6202

# Same absolute paths as the builder stage: the recorded classpath.txt
# contains absolute jar/dir paths under /build/... and /root/.m2 /
# /root/.gitlibs, so the runtime stage keeps that layout verbatim.
COPY --from=builder /build /build
COPY --from=builder /root/.m2 /root/.m2
COPY --from=builder /root/.gitlibs /root/.gitlibs

WORKDIR /build/orgs/cloud-itonami/cloud-itonami-isic-6202

# svcdesk.file-store snapshots (ISIC6202_STORE_FILE) and Ring's multipart
# temp files may need a writable dir; keep the whole tree owned by the
# non-root user rather than special-casing paths. /root itself defaults
# to 700 (root-only traversal) -- widen it so the non-root user can
# reach the copied .m2/.gitlibs caches underneath.
RUN chmod 755 /root \
    && chown -R isic6202:isic6202 /root/.m2 /root/.gitlibs /build

USER isic6202

# Secrets/config are read from the container's environment ONLY --
# never baked into the image. ISIC6202_API_TOKEN has no default: this
# mirrors svcdesk.http/-main's own fail-closed contract (refuses to
# start without it) rather than silently supplying one.
ENV ISIC6202_HTTP_PORT=8080
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD wget -q -O- "http://127.0.0.1:${ISIC6202_HTTP_PORT}/health" || exit 1

CMD ["sh", "-c", "exec java -cp \"$(cat /build/classpath.txt)\" clojure.main -m svcdesk.http"]
