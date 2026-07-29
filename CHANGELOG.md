# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.1.9] - 2026-07-29

### Changed

- **Repository is now a multi-module Maven build.** A new aggregator pom
  `org.opentmf.mockserver:opentmf-mockserver-parent` (packaging=pom) sits at the repository
  root and reactor-builds the existing `org.opentmf.mockserver:opentmf-mockserver` jar under
  the `opentmf-mockserver/` subdirectory. **The server artifact's coordinates
  (`groupId:artifactId:version`) are unchanged**, so no consumer changes are required. The
  restructure exists to host the new `opentmf-mockserver-test-support` sibling artifact
  without polluting the server jar's dependency surface with JUnit and Spring. Plugin versions
  and configuration now live in the aggregator's `<pluginManagement>`; release-only plugins
  (`maven-source-plugin`, `maven-javadoc-plugin`, `maven-gpg-plugin`,
  `central-publishing-maven-plugin`) live in the aggregator's `release` profile.

## [2.1.8] - 2026-07-15

### Added

- **`PayloadCache.putIfAbsent`** — atomic insert-if-absent under the cache lock. Returns `true`
  on insert, `false` when the id was already present. Used by every create callback (POST,
  PUT-create, batch collection PATCH) in place of the historical `CACHE.get` → `CACHE.put`
  idiom, which had a check-then-act window that surfaced as spurious `500`s under concurrent
  duplicate-id creates.

### Fixed

- **Concurrent creates with the same client-supplied id no longer leak a `500`.**
  `DynamicPostCallback`, `DynamicPutCallback`'s create branch, and
  `DynamicJsonPatchCollectionCallback` used a `CACHE.get(...) != null` uniqueness check
  followed by a separate `CACHE.put(...)`. Two callbacks racing on the same id both saw "not
  present," both reached `put()`, and the second raised
  `IllegalArgumentException("Key: [...] already exists in cache for domain ")` — surfaced by
  MockServer as `500 Internal Server Error`, not the intended `400`/`409`. All three paths now
  use `PayloadCache.putIfAbsent` under the cache lock. POST losers get their intended
  `400 "already exists"`. PUT losers degrade to an idempotent replace (`200`) on the winning
  creator's resource — PUT is idempotent, so the two paths converge to the same observable
  state. Batch collection PATCH losers get `409` and every item the losing batch had already
  inserted is rolled back so the batch stays atomic per RFC 5789. Regression tests fire 8–20
  concurrent racers per callback and assert exactly one winner, correct loser status, and zero
  `500`s.
- **`DynamicGetCallback` no longer mutates the cached `JsonNode` in place on first-observation
  state transition.** When a GET landed on an entity still in its initial state (e.g.
  `status: "created"`), the callback cast the live cached node to `ObjectNode` and directly
  `put`'d the final state plus `updatedDate` / `updatedBy` / `revision++` on it — outside any
  cache lock. The 2.1.7 snapshot fix rests on the invariant "write paths replace nodes, never
  mutate them in place," which is what makes it safe for the list-GET path to iterate
  `PayloadCache.getAll()`'s snapshot outside the cache lock: readers keep sharing the same
  `JsonNode` references, so those nodes must be effectively immutable. GET breaking that
  invariant meant a concurrent single-GET's transition could race a list-GET's iteration or
  serialization of the same node — Jackson's `ObjectNode` is backed by a plain
  `LinkedHashMap`, so concurrent mutation during another thread's walk is undefined
  (`ConcurrentModificationException`, or a serialised payload with `revision` bumped but
  `updatedDate` not yet stamped). The transition now runs on a deep-copy, and the cache
  reference is swapped atomically via `PayloadCache.update`; readers holding the old reference
  see a stable, pre-transition node. Regression test in `DynamicGetCallbackTests` pins the
  reference-replacement semantics.

### Changed

- Bumped runtime dependencies: Netty 4.2.16.Final, netty-tcnative 2.0.80.Final,
  json-schema-validator 3.0.6.

## [2.1.7] - 2026-07-07

### Fixed

- **Filtered list GETs no longer intermittently miss freshly created entities under concurrent
  load.** `PayloadCache.getAll()` was `synchronized` but returned the <em>live</em> internal
  `TreeMap`; `DynamicGetListCallback` then iterated and filtered that map outside the cache lock,
  concurrently with `put()`s mutating the same tree. Under parallel clients this raced the tree's
  structural changes and intermittently skipped entries that had already been created and
  acknowledged with 201 — observed as ~1% `404 Not Found` on `GET ?id=…&version=…` list queries
  seconds after the entity's creation (dsync parallel rehearsal, 2026-07-07: 26 of 2,385 such
  queries failed; two orchestrated updates exhausted their retries against these phantom 404s).
  Sequential clients could never hit this, which is why the defect survived every serial test.
  `getAll()` now returns a shallow snapshot taken under the lock. The `JsonNode` values remain
  shared: the write paths replace nodes and never mutate them in place, so a snapshot reader
  always sees a consistent entity. Regression tests pin the snapshot semantics
  (`PayloadCacheTests`).

## [2.1.6] - 2026-06-26

### Added

- **Configurable required roles per HTTP method.** Five new env vars `ROLES_GET`, `ROLES_POST`,
  `ROLES_PUT`, `ROLES_PATCH`, `ROLES_DELETE` accept a comma-separated list of role names; a request
  whose token carries any one of the listed roles passes. Defaults preserve the historical
  behaviour (`reader,writer,admin` for GET; `writer,admin` for POST/PUT/PATCH; `admin` for DELETE).
  Setting a variable to the empty string disables the role check for that method while still
  validating signature, expiry, and issuer. The Keycloak Admin REST API endpoints remain hard-coded
  to require `admin`.
- **Configurable roles claim path.** New env var `ROLES_CLAIM_PATH` accepts a dotted JSON path into
  the token payload (e.g. `resource_access.my-client.roles`, `groups`, or a namespaced claim like
  `https://example.com/roles`). The leaf must resolve to a JSON array of strings. When unset, the
  enforcer keeps its previous fallback chain (`realm_access.roles`, then top-level `roles`); when
  set, the configured path is the single source of truth and the default fallback is not consulted.

### Changed

- `TokenEnforcer` gains a `validateForRequest(HttpRequest)` helper that resolves the required
  roles for the request's HTTP method from the configured per-method map. All eight TMF dynamic
  callbacks (`DynamicGetCallback`, `DynamicGetListCallback`, `DynamicPostCallback`,
  `DynamicPutCallback`, `DynamicJsonPatchCallback`, `DynamicMergePatchCallback`,
  `DynamicJsonPatchCollectionCallback`, `DynamicDeleteCallback`) now route through it instead of
  passing a hard-coded role list.

## [2.1.5] - 2026-06-19

### Added

- **`Idempotency-Key` request header** is now recognised on every mutating callback (POST, PUT,
  PATCH `application/merge-patch+json`, PATCH `application/json-patch+json` on a single resource,
  PATCH `application/json-patch+json` on a collection, DELETE). When a client retries with the same
  key on the same `(method, path)`, the server replays the original 2xx response verbatim with an
  `X-Idempotent-Replay: true` marker and resets the underlying resource's cache TTL counter — a
  "touch" — so the resource lives at least as long as clients keep retrying. Missing/blank keys are
  ignored (clients without idempotency awareness behave exactly as before). Same key reused on a
  different `(method, path)` returns **422 Unprocessable Entity**; keys longer than 255 characters
  return **400**. Idempotency records expire on the same TTL as `PayloadCache`
  (`CACHE_DURATION_MILLIS`, default 2 h) and are also evicted eagerly when the underlying resource
  is removed by TTL, so a same-key retry after eviction is treated as a fresh request.

### Changed

- `PayloadCache#evictOldItems` now notifies `IdempotencyCache` for each evicted `(domain, id)` so
  stale replays cannot survive their underlying resource.
- New public helper `PayloadCache#touchByResource(domain, id)` performs a point-touch (the existing
  `touch(ctx)` does a range-touch); used by the idempotency replay path.

## [2.1.4] - 2026-05-28

### Added

- **HTTP PUT endpoint**: New `DynamicPutCallback` implements RFC 9110 §9.3.4 PUT semantics on
  `PUT /{basePath}/{id}`. The request body is the complete desired state of the resource; the
  operation is idempotent. Creates the resource (**201**) when not yet cached and replaces it
  wholesale (**200**) when it exists. The URI's id (and optional `:(version=XYZ)`) is
  authoritative — a conflicting body `id`/`version` returns **400**. On replace, `id`, `version`,
  `href`, `createdBy`, and `createdDate` are carried over from the existing entry, `revision` is
  incremented, and `updatedBy`/`updatedDate` are stamped.

### Changed

- `DynamicPostCallback` exposes a new public static helper `prepareForCacheWithHref(ctx, body,
  href)` so callers whose request path already contains the resource id (e.g. PUT) can reuse the
  same prep flow without the doubled-id `href` that `ctx.toHref()` would produce.

## [2.1.3] - 2026-05-10

### Added

- **TMF630 §6.2 bulk-create endpoint**: New `DynamicJsonPatchCollectionCallback` implements
  TMF630 Part 1 §6.2 "Creating Multiple Resources". Bound to `PATCH /{basePath}` (the collection
  URL, no id segment) with `Content-Type: application/json-patch+json`. Body is a non-empty JSON
  array of `{"op":"add", "path":"/", "value":{...}}` operations; each value goes through the same
  flow as a single POST (id generation, `href`, initial state, audit fields, `ADDITIONAL_FIELDS`).
  Atomic per RFC 5789: a duplicate id within the batch or against the cache returns 409 Conflict
  with no resources committed. Response is 200 with the array of created resources, honoring
  `?fields=` (including the `fields=none` sentinel).
- **`fields=none` support on GET callbacks**: `DynamicGetCallback` and `DynamicGetListCallback`
  now recognize `?fields=none` (case-insensitive) as a TMF630 sentinel that projects each
  returned resource to only `id` and `href`. Mixed lists like `fields=none,description` continue
  to be treated as literal field names.

### Changed

- `DynamicPostCallback` extracts a public static helper `prepareForCache(ctx, parsedBody)` so the
  bulk-create callback can reuse the per-item POST flow without going through the cache.
- Bumped runtime dependencies: Jackson 3.1.3, Netty 4.2.13.Final, netty-tcnative 2.0.77.Final,
  BouncyCastle 1.84, nimbus-jose-jwt 10.9, json-schema-validator 3.0.2, Commons Codec 1.22.0,
  Guava 33.6.0-jre.

## [2.1.2] - 2026-03-25

### Added

- **Configurable token lifetime**: `expiresIn` (seconds) can be set per realm or per client in the
  Keycloak configuration. Client-level overrides realm-level; default is 3600s when both are omitted.

## [2.1.1] - 2026-03-24

### Added

- **Keycloak Admin REST API mock**: Read-only `GET /admin/realms/{realm}/...` endpoints returning
  Keycloak-compatible JSON representations for users, groups, roles, and clients. Includes
  filtering (`username`, `search`), pagination (`first`, `max`), and sub-resource navigation
  (user role-mappings, user groups, group members, role users). Requires `admin` role when
  `ENFORCE_TOKEN=true`.
- **Group support**: New `GroupConfig` model with `name` and `subGroups`. Groups are configurable
  per realm and exposed via the Admin API.
- **Extended user metadata**: `UserConfig` now supports `email`, `firstName`, `lastName`, and
  `groups` fields, included in Admin API user representations and the default configuration.
- **Enriched default configuration**: `default-keycloak-config.json` now includes groups
  (`admins`, `developers`, `viewers`), user metadata (email, name), and explicit
  `serviceAccountRoles` for all clients.

### Changed

- Marked `jsr305` (compile-time annotations) and `slf4j-jdk14` (SLF4J binding) as
  `<optional>true</optional>` so they are not pulled transitively by consumers.

## [2.1.0] - 2026-03-22

### Changed

- **Jackson 3.x migration**: Migrated from Jackson 2.x (`com.fasterxml.jackson`) to Jackson 3.x
  (`tools.jackson`). This project no longer pulls any Jackson 2.x transitive dependencies, making
  it compatible with Spring Boot 4.x and other Jackson 3.x consumers.
- **Embedded MockServer core**: Source code from
  [mock-server/mockserver](https://github.com/mock-server/mockserver) v5.15.0 (Apache-2.0, by
  James D Bloom) is now bundled directly instead of depending on the external `mockserver-netty`
  artifact. This enabled the Jackson 3.x migration and the removal of unused features.
- **JSON Patch and Merge Patch** now use `org.opentmf:opentmf-json-patch:1.1.0`.
- Upgraded all runtime dependencies to their latest versions. All dependency versions are now
  managed via Maven properties for easy tracking with `mvn versions:display-property-updates`.
  Notable updates: Netty 4.2.10.Final, BouncyCastle 1.83, nimbus-jose-jwt 10.8,
  json-schema-validator 3.0.1, json-path 3.0.0, Guava 33.5.0-jre, Commons Lang3 3.20.0,
  SLF4J 2.0.17.

### Removed

- External `org.mock-server:mockserver-netty` and `org.mock-server:mockserver-client-java`
  dependencies.
- UI dashboard, proxy/SOCKS support, template engines (JavaScript/Velocity), XML/XPath/XmlSchema
  body matching, OpenAPI/Swagger expectation support, and Prometheus metrics -- these features are
  not needed for TMF mock usage and carried heavy transitive dependencies.
- `javax.servlet` dependency.

## [2.0.0] - 2026-03-21

### Added

- **Real JWT token generation**: `KeycloakTokenCallback` produces real, parsable, RSA-signed JWTs.
- **JWKS endpoint**: `GET /.well-known/jwks.json` is served automatically at startup via
  `JwksExpectationInitializer`, allowing clients to validate issued tokens.
- **Keycloak mock**: Configurable multi-realm Keycloak simulation with support for
  `client_credentials`, `password`, and `refresh_token` grant types. Realm-specific endpoints:
    - `GET /realms/{realm}/protocol/openid-connect/certs` (JWKS)
    - `GET /realms/{realm}/.well-known/openid-configuration` (OIDC discovery)
    - `POST /realms/{realm}/protocol/openid-connect/token` (token endpoint)
- **Keycloak configuration**: Realms, clients (public/confidential), users, roles, and grant types
  are configurable via a JSON file (`KEYCLOAK_CONFIG` env var or
  `keycloak.config.path` system property). Ships with a sensible default configuration.
- **Token enforcement**: All dynamic callbacks (`Post`, `Get`, `GetList`, `JsonPatch`,
  `MergePatch`, `Delete`) can enforce Bearer JWT validation via the `ENFORCE_TOKEN` environment
  variable. Supports validation against:
    - Built-in mock JWKS keys
    - An external JWKS URI (`JWKS_URI` env var)
    - OIDC auto-discovery via issuer (`TOKEN_ISSUER` env var)
- **Issuer validation**: When `TOKEN_ISSUER` is set, the `iss` claim is validated in addition to
  the signature.
- **Role-based authorization**: When `ENFORCE_TOKEN=true`, each dynamic callback enforces
  role requirements from the JWT's `realm_access.roles` claim:
    - GET / GET List: requires `reader`, `writer`, or `admin`
    - POST / PATCH: requires `writer` or `admin`
    - DELETE: requires `admin`
- **Integration tests**: Keycloak Testcontainers integration test (`KeycloakIntegrationIT`) runs
  during `mvn integration-test` / `mvn verify` via the Maven Failsafe plugin, validating real
  Keycloak tokens against `TokenEnforcer`.

### Removed

- `OpenidTokenCallback`, `OpenidTokenGenerator`, `TokenGenerator`, and `TokenException` -- superseded
  by `KeycloakTokenCallback` which provides Keycloak-compatible token issuance with strict validation.

### Fixed

- `DynamicPostCallback` now returns HTTP 201 Created instead of 200 OK.
- `DynamicDeleteCallback` now retrieves version from payload when needed for versioned entities.
- `DynamicGetListCallback` `compare()` handles null values without throwing.
- `DynamicGetListCallback` sort extraction now uses `LinkedHashSet` to preserve sort order.
- `DynamicJsonPatchCallback` and `DynamicMergePatchCallback` now call `setUpdateFields()` after
  patching to correctly set `updatedDate`, `updatedBy`, and `revision`.
- `PayloadCache` is now thread-safe (singleton pattern, synchronized access, bounds-checked
  `allOf()`), with correct TTL eviction and `touch()` behavior.
- Removed unused `PayloadCache.clear(String, Id)` method.

## [1.1.1] - 2025-11-27

### Fixed

- Started returning 416 Range Not Satisfiable when `offset > 0` and `offset >= totalCount`.
- Started supporting `CONTENT_RANGE_OFFSET_BASE` environment variable (accepts 0 or 1, default 1).
- Started setting `X-Result-Count` header on `getList`.

## [1.1.0] - 2025-11-07

### Fixed

- Fixes the Docker image again. 1.0.8 and 1.0.9 did not behave as expected.

## [1.0.9] - 2025-11-07

### Fixed

- Fixes the Docker image.

## [1.0.8] - 2025-11-07

### Changed

- Applies query parameters filter to the cached domain payloads in GET LIST.

## [1.0.7] - 2025-09-28

### Fixed

- `RequestContext` initialization is performed on the decoded URL string.

## [1.0.6] - 2025-05-29

### Added

- Version resolution from query parameters (`?version=XYZ`).
- `ADDITIONAL_FIELDS` environment variable support.
- `CACHE_DURATION_MILLIS` environment variable support.

## [1.0.5] - 2025-05-25

### Fixed

- Fixed the target JAR file path in the release Dockerfile.

## [1.0.4] - 2025-05-22

### Added

- Introduced a release Dockerfile.

## [1.0.3] - 2025-05-22

### Changed

- Enhanced the version resolving algorithm.

## [1.0.2] - 2025-04-11

### Changed

- First open-source version.

## [1.0.1]

### Added

- Support for versioned entities (TMF-630 Part 4.2).

## [1.0.0]

### Added

- Initial release.

[2.1.9]: https://github.com/opentmf/opentmf-mockserver/compare/2.1.8...2.1.9

[2.1.8]: https://github.com/opentmf/opentmf-mockserver/compare/2.1.7...2.1.8

[2.1.7]: https://github.com/opentmf/opentmf-mockserver/compare/2.1.6...2.1.7

[2.1.6]: https://github.com/opentmf/opentmf-mockserver/compare/2.1.5...2.1.6

[2.1.5]: https://github.com/opentmf/opentmf-mockserver/compare/2.1.4...2.1.5

[2.1.4]: https://github.com/opentmf/opentmf-mockserver/compare/2.1.3...2.1.4

[2.1.3]: https://github.com/opentmf/opentmf-mockserver/compare/2.1.2...2.1.3

[2.1.2]: https://github.com/opentmf/opentmf-mockserver/compare/2.1.1...2.1.2

[2.1.1]: https://github.com/opentmf/opentmf-mockserver/compare/2.1.0...2.1.1

[2.1.0]: https://github.com/opentmf/opentmf-mockserver/compare/2.0.0...2.1.0

[2.0.0]: https://github.com/opentmf/opentmf-mockserver/compare/1.1.1...2.0.0

[1.1.1]: https://github.com/opentmf/opentmf-mockserver/compare/1.1.0...1.1.1

[1.1.0]: https://github.com/opentmf/opentmf-mockserver/compare/1.0.9...1.1.0

[1.0.9]: https://github.com/opentmf/opentmf-mockserver/compare/1.0.8...1.0.9

[1.0.8]: https://github.com/opentmf/opentmf-mockserver/compare/opentmf-mockserver-1.0.7...1.0.8

[1.0.7]: https://github.com/opentmf/opentmf-mockserver/compare/opentmf-mockserver-1.0.6...opentmf-mockserver-1.0.7

[1.0.6]: https://github.com/opentmf/opentmf-mockserver/compare/opentmf-mockserver-1.0.5...opentmf-mockserver-1.0.6

[1.0.5]: https://github.com/opentmf/opentmf-mockserver/compare/opentmf-mockserver-1.0.4...opentmf-mockserver-1.0.5

[1.0.4]: https://github.com/opentmf/opentmf-mockserver/compare/opentmf-mockserver-1.0.3...opentmf-mockserver-1.0.4

[1.0.3]: https://github.com/opentmf/opentmf-mockserver/compare/opentmf-mockserver-1.0.2...opentmf-mockserver-1.0.3

[1.0.2]: https://github.com/opentmf/opentmf-mockserver/tag/opentmf-mockserver-1.0.2

[1.0.1]: https://github.com/opentmf/opentmf-mockserver/releases/tag/1.0.1

[1.0.0]: https://github.com/opentmf/opentmf-mockserver/releases/tag/1.0.0
