# Implementation plan — `opentmf-mockserver-test-support`

**Status:** plan only (2026-07-28, authored for the dnms-rules effort). To be
implemented in a dedicated opentmf session.

**Decision context.** Consumer projects (dsync services today, every `dnms-*`
service tomorrow) each hand-roll the same integration-test glue around
opentmf-mockserver: start `ClientAndServer`, register `Dynamic*Callback`
expectations, wire the OIDC mock, redirect `opentmf.api-clients` base-urls, and
verify received calls. dsync-engine's `OrchestrateE2eIT` shows the raw pattern
(static `request()/response()` builders + `ClientAndServer` + manual
verifications); the dnms rules call for a shared `MockServerUtils`-style helper.
**Decision: the helper ships INSIDE this repo as a test-support artifact**, not as
a per-service copy — one implementation, versioned with the server it drives, no
drift across consumers.

---

## 1. Packaging

Convert the repo to a small multi-module build:

```
opentmf-mockserver/                  (aggregator pom)
  opentmf-mockserver/                (the existing single module, unchanged coordinates)
  opentmf-mockserver-test-support/   (NEW, this plan)
```

- The existing artifact `org.opentmf.mockserver:opentmf-mockserver` keeps its
  coordinates so no consumer breaks; only its parent changes to the new aggregator.
- `opentmf-mockserver-test-support` depends on `opentmf-mockserver` plus
  `org.junit.jupiter:junit-jupiter-api` and `spring-test`/`spring-boot-test`
  (all **provided/optional** so the module works for plain-JUnit consumers and
  Spring consumers alike, without dragging Spring into non-Spring builds).
- Both modules stay version-managed by `opentmf-versions` (add the new artifact
  to the BOM in the same release).

*Rejected alternative:* putting the helper classes into the existing artifact.
The server jar is a shaded/standalone-capable application artifact; test-support
needs JUnit/Spring types on its compile path and would pollute the server's
dependency surface. A separate test-scoped artifact keeps both clean.

## 2. Public API (the whole point — what consumers write)

Package `org.opentmf.mockserver.testsupport`. Target consumer code:

```java
@RegisterExtension
static MockServerSupport mock = MockServerSupport.create();   // random free port

@DynamicPropertySource
static void redirect(DynamicPropertyRegistry registry) {
  mock.redirectApiClients(registry, "onedms", "asgw");        // base-url -> mock, context-path -> ""
  mock.redirectJwks(registry);                                // opentmf.security.jwk-set-uri -> mock
}

@Test
void testArchive_withValidDocument_returns201() {
  mock.tmf("onedms").post("/document");                       // registers DynamicPostCallback
  mock.stub().get("/kba/{key}").respondJson(200, kbaJson);    // static expectation, fluent
  ...
  mock.verify().post("/document").times(1).withJsonPath("$.id", docId);
}
```

### 2.1 `MockServerSupport` (JUnit 5 extension + facade)

- Implements `BeforeAllCallback`/`AfterAllCallback`/`AfterEachCallback`:
  starts one `ClientAndServer` per test class on a random free port
  (`PortFactory.findFreePort()`), stops it after, `reset()`s expectations after
  each test (opt-out via `create().keepExpectationsBetweenTests()`).
- Also usable WITHOUT JUnit (plain `create()/start()/stop()`) for Cucumber/E2E
  harnesses (the dnms compose release-gate).
- Exposes `port()`, `baseUrl()`, `client()` (the underlying `MockServerClient`).

### 2.2 `TmfMockBuilder` — dynamic TMF resource mocking

Fluent wrapper over the existing callbacks (this is where the repo's unique value
is — the callbacks exist, registering them is today's boilerplate):

- `mock.tmf("onedms").post("/document")` → expectation with
  `DynamicPostCallback`; same for `get/getList/put/delete/jsonPatch/mergePatch/
  jsonPatchCollection`, mapping 1:1 to the `org.opentmf.mockserver.callback.*`
  classes.
- `crud(String path)` convenience = post + get + getList + put + delete in one call.
- Passes through the callback conventions unchanged (auto `id`/`href`/audit
  fields, TMF paging headers, state auto-transition, Idempotency-Key handling per
  MOCKSERVER.md).

### 2.3 `StubBuilder` — static expectations

Thin fluent layer over `request()/response()` for the non-TMF cases (KBA lookups,
SMTP-gateway APIs, DXL): `method/path/pathParam/queryParam/header/jsonBody` →
`respondJson(status, body)`, `respondStatus(status)`, `respondDelayed(status, body,
Duration)` (timeout/retry testing), `respondSequence(...)` (first call 500, then
200 — retry-path tests).

### 2.4 `OidcMockSupport`

- `redirectJwks(registry)` → points `opentmf.security.jwk-set-uri` at the mock's
  `/realms/{realm}/protocol/openid-connect/certs` (default realm `realm1`).
- `token(String... roles)` / `tokenFor(String user, String... roles)` → mints a
  signed JWT via the built-in Keycloak mock (reuses `org.opentmf.mockserver.util.
  TokenUtil` / `JwtKeyProvider`) for `Authorization: Bearer` headers in MockMvc/
  RestClient IT calls. This replaces the per-service `TokenUtil` +
  `jwk-set-test.json` copies (dsync-engine has one; dnms services would each need
  one).
- `enforceTokens()` toggle mapping to `ENFORCE_TOKEN` semantics for the
  reader/writer/admin matrix.

### 2.5 `VerifyBuilder`

`mock.verify().post("/document")` → `.times(n)`, `.never()`, `.inOrder(other)`,
`.withJsonPath(path, expected)`, `.withHeader(name, value)` — wrapping
`MockServerClient.verify(...)` + `VerificationTimes` so consumers stop importing
`org.mockserver.*` internals directly.

### 2.6 `redirectApiClients(registry, String... clientIds)`

For each id: `opentmf.api-clients.<id>.base-url` → `mock.baseUrl()`,
`opentmf.api-clients.<id>.context-path` → `""` (the established
`@DynamicPropertySource` idiom, centralized). Overload for
`opentmf.http-clients.<id>.base-url` (raw clients) and for the bearer-auth
`token-url` → the mock's token endpoint.

## 3. Out of scope (explicitly)

- No Testcontainers management — infrastructure containers belong to the
  consumer's IT setup; this module only covers the HTTP-mock + OIDC surface.
- No WireMock compatibility layer.
- No changes to callback behavior — the module is additive glue only.

## 4. Work breakdown

1. Multi-modulize the repo (aggregator + move existing sources) — coordinates
   unchanged; CI/Dockerfile paths adjusted. *(±half day)*
2. `MockServerSupport` + lifecycle extension + port handling + tests. *(half day)*
3. `TmfMockBuilder` + `StubBuilder` + `VerifyBuilder` + tests (the tests double
   as usage documentation). *(1 day)*
4. `OidcMockSupport` (reuse TokenUtil/JwtKeyProvider) + tests. *(half day)*
5. `redirectApiClients`/`redirectJwks` + a sample Spring Boot IT in
   `opentmf-mockserver-test-support`'s own test tree proving the end-to-end idiom
   against a toy `opentmf.api-clients` config. *(half day)*
6. README section + CHANGELOG entry; add the artifact to `opentmf-versions`;
   release. *(half day)*
7. Follow-up (separate PR, optional): migrate dsync-engine's `OrchestrateE2eIT`
   + `TokenUtil` to the new module as the reference consumer.

## 5. Acceptance criteria

- A consumer IT needs ZERO imports from `org.mockserver.*` for the standard
  cases (dynamic TMF mock, static stub, token minting, redirect, verify).
- Works in plain JUnit 5 (no Spring on classpath) and in `@SpringBootTest`.
- Coverage gate per opentmf standards; javadoc on every public type.
- No behavior change to the existing server artifact.
