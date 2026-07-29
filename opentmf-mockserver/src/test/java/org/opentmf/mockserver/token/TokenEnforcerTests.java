package org.opentmf.mockserver.token;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockserver.model.HttpRequest.request;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.SignedJWT;
import io.hypersistence.tsid.TSID;
import java.io.Serializable;
import java.text.ParseException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

@SuppressWarnings("java:S5976") // parameterized-vs-separate is a style preference; separate cases keep failure messages self-documenting
class TokenEnforcerTests {

  private static final String ISSUER = "https://mockserver/test";
  private static JWKSource<SecurityContext> localKeySource;

  @BeforeAll
  static void initKeySource() throws ParseException {
    JWKSet keys = JWKSet.parse(JwtKeyProvider.getInstance().getJwksJson());
    localKeySource = new ImmutableJWKSet<>(keys);
  }

  private TokenEnforcer enabledEnforcer(String expectedIssuer) {
    return new TokenEnforcer(true, expectedIssuer, localKeySource);
  }

  private TokenEnforcer disabledEnforcer() {
    return new TokenEnforcer(false, "", localKeySource);
  }

  /**
   * Signs a token using epoch seconds for iat/exp so that Nimbus parses
   * them correctly as NumericDate values (matching real-world JWTs).
   */
  private String signToken(String issuer, String subject, long expiresInMs) {
    long nowSeconds = System.currentTimeMillis() / 1000;
    Map<String, Serializable> claims = new LinkedHashMap<>();
    claims.put("iss", issuer);
    claims.put("sub", subject);
    claims.put("iat", nowSeconds);
    claims.put("exp", nowSeconds + expiresInMs / 1000);
    claims.put("jti", TSID.Factory.getTsid().toString());
    return JwtKeyProvider.getInstance().signJwt(claims);
  }

  private String signTokenWithRoles(String issuer, String subject, long expiresInMs,
      List<String> roles) {
    long nowSeconds = System.currentTimeMillis() / 1000;
    Map<String, Serializable> claims = new LinkedHashMap<>();
    claims.put("iss", issuer);
    claims.put("sub", subject);
    claims.put("iat", nowSeconds);
    claims.put("exp", nowSeconds + expiresInMs / 1000);
    claims.put("jti", TSID.Factory.getTsid().toString());

    LinkedHashMap<String, Serializable> realmAccess = new LinkedHashMap<>();
    realmAccess.put("roles", (Serializable) roles);
    claims.put("realm_access", realmAccess);
    return JwtKeyProvider.getInstance().signJwt(claims);
  }

  // ---- disabled mode ----

  @Test
  void disabledEnforcer_alwaysPasses() {
    TokenEnforcer enforcer = disabledEnforcer();
    HttpRequest req = request().withMethod("GET").withPath("/test");
    assertNull(enforcer.validate(req));
  }

  @Test
  void disabledEnforcer_passesEvenWithoutAuthHeader() {
    TokenEnforcer enforcer = disabledEnforcer();
    assertNull(enforcer.validate(request().withPath("/test")));
  }

  // ---- enabled mode with local keys ----

  @Test
  void validToken_passes() {
    TokenEnforcer enforcer = enabledEnforcer("");
    String token = signToken(ISSUER, "user1", 3600_000);

    HttpRequest req = request().withPath("/api/test")
        .withHeader("Authorization", "Bearer " + token);

    assertNull(enforcer.validate(req));
  }

  @Test
  void missingAuthHeader_returns401() {
    TokenEnforcer enforcer = enabledEnforcer("");
    HttpRequest req = request().withPath("/api/test");

    HttpResponse resp = enforcer.validate(req);
    assertNotNull(resp);
    assertEquals(401, resp.getStatusCode());
    assertTrue(resp.getBodyAsString().contains("Missing"));
  }

  @Test
  void emptyBearerToken_returns401() {
    TokenEnforcer enforcer = enabledEnforcer("");
    HttpRequest req = request().withPath("/api/test")
        .withHeader("Authorization", "Bearer ");

    HttpResponse resp = enforcer.validate(req);
    assertNotNull(resp);
    assertEquals(401, resp.getStatusCode());
  }

  @Test
  void nonBearerScheme_returns401() {
    TokenEnforcer enforcer = enabledEnforcer("");
    HttpRequest req = request().withPath("/api/test")
        .withHeader("Authorization", "Basic abc123");

    HttpResponse resp = enforcer.validate(req);
    assertNotNull(resp);
    assertEquals(401, resp.getStatusCode());
  }

  @Test
  void garbageToken_returns401() {
    TokenEnforcer enforcer = enabledEnforcer("");
    HttpRequest req = request().withPath("/api/test")
        .withHeader("Authorization", "Bearer not.a.jwt");

    HttpResponse resp = enforcer.validate(req);
    assertNotNull(resp);
    assertEquals(401, resp.getStatusCode());
  }

  @Test
  void expiredToken_returns401() {
    TokenEnforcer enforcer = enabledEnforcer("");
    // Token expired 5 minutes ago (well beyond any clock skew tolerance)
    String token = signToken(ISSUER, "user1", -300_000);

    HttpRequest req = request().withPath("/api/test")
        .withHeader("Authorization", "Bearer " + token);

    HttpResponse resp = enforcer.validate(req);
    assertNotNull(resp);
    assertEquals(401, resp.getStatusCode());
  }

  // ---- issuer validation ----

  @Test
  void correctIssuer_passes() {
    TokenEnforcer enforcer = enabledEnforcer(ISSUER);
    String token = signToken(ISSUER, "user1", 3600_000);

    HttpRequest req = request().withPath("/api/test")
        .withHeader("Authorization", "Bearer " + token);

    assertNull(enforcer.validate(req));
  }

  @Test
  void wrongIssuer_returns401() {
    TokenEnforcer enforcer = enabledEnforcer("https://expected-issuer");
    String token = signToken("https://wrong-issuer", "user1", 3600_000);

    HttpRequest req = request().withPath("/api/test")
        .withHeader("Authorization", "Bearer " + token);

    HttpResponse resp = enforcer.validate(req);
    assertNotNull(resp);
    assertEquals(401, resp.getStatusCode());
    assertTrue(resp.getBodyAsString().contains("Issuer mismatch"));
  }

  @Test
  void noIssuerConfigured_anyIssuerPasses() {
    TokenEnforcer enforcer = enabledEnforcer("");
    String token = signToken("https://any-issuer", "user1", 3600_000);

    HttpRequest req = request().withPath("/api/test")
        .withHeader("Authorization", "Bearer " + token);

    assertNull(enforcer.validate(req));
  }

  // ---- response format ----

  @Test
  void unauthorizedResponse_hasWwwAuthenticateHeader() {
    TokenEnforcer enforcer = enabledEnforcer("");
    HttpRequest req = request().withPath("/api/test");

    HttpResponse resp = enforcer.validate(req);
    assertNotNull(resp);
    String wwwAuth = resp.getFirstHeader("WWW-Authenticate");
    assertNotNull(wwwAuth);
    assertTrue(wwwAuth.startsWith("Bearer"));
  }

  @Test
  void unauthorizedResponse_hasJsonBody() {
    TokenEnforcer enforcer = enabledEnforcer("");
    HttpRequest req = request().withPath("/api/test");

    HttpResponse resp = enforcer.validate(req);
    assertNotNull(resp);
    assertTrue(resp.getBodyAsString().contains("invalid_token"));
  }

  // ---- singleton (default config is disabled) ----

  @Test
  void singletonDefaultIsDisabled() {
    TokenEnforcer singleton = TokenEnforcer.getInstance();
    assertNull(singleton.validate(request().withPath("/test")));
  }

  // ---- keycloak token integration ----

  @Test
  void keycloakToken_validatedByLocalKeys() throws Exception {
    String realmIssuer = "http://localhost:1080/realms/realm1";
    TokenEnforcer enforcer = enabledEnforcer(realmIssuer);

    long nowSec = System.currentTimeMillis() / 1000;
    Map<String, Serializable> claims = new LinkedHashMap<>();
    claims.put("iss", realmIssuer);
    claims.put("sub", "admin_usr");
    claims.put("typ", "Bearer");
    claims.put("azp", "client2");
    claims.put("iat", nowSec);
    claims.put("exp", nowSec + 3600);
    claims.put("jti", TSID.Factory.getTsid().toString());
    String token = JwtKeyProvider.getInstance().signJwt(claims);

    HttpRequest req = request().withPath("/tmf-api/serviceOrder/v4/serviceOrder")
        .withHeader("Authorization", "Bearer " + token);

    assertNull(enforcer.validate(req));

    SignedJWT parsed = SignedJWT.parse(token);
    assertEquals("admin_usr", parsed.getJWTClaimsSet().getSubject());
    assertEquals(realmIssuer, parsed.getJWTClaimsSet().getIssuer());
  }

  // ---- role-based authorization ----

  @Test
  void tokenWithAdminRole_passesAdminCheck() {
    TokenEnforcer enforcer = enabledEnforcer("");
    String token = signTokenWithRoles(ISSUER, "admin_usr", 3600_000,
        Arrays.asList("admin", "writer", "reader"));

    assertNull(enforcer.validateWithRoles(
        request().withPath("/api/test").withHeader("Authorization", "Bearer " + token),
        "admin"));
  }

  @Test
  void tokenWithReaderRole_passesReaderWriterAdminCheck() {
    TokenEnforcer enforcer = enabledEnforcer("");
    String token = signTokenWithRoles(ISSUER, "reader_usr", 3600_000,
        List.of("reader"));

    assertNull(enforcer.validateWithRoles(
        request().withPath("/api/test").withHeader("Authorization", "Bearer " + token),
        "reader", "writer", "admin"));
  }

  @Test
  void tokenWithReaderRole_failsWriterAdminCheck() {
    TokenEnforcer enforcer = enabledEnforcer("");
    String token = signTokenWithRoles(ISSUER, "reader_usr", 3600_000,
        List.of("reader"));

    HttpResponse resp = enforcer.validateWithRoles(
        request().withPath("/api/test").withHeader("Authorization", "Bearer " + token),
        "writer", "admin");
    assertNotNull(resp);
    assertEquals(403, resp.getStatusCode());
    assertTrue(resp.getBodyAsString().contains("insufficient_scope"));
  }

  @Test
  void tokenWithReaderRole_failsAdminOnlyCheck() {
    TokenEnforcer enforcer = enabledEnforcer("");
    String token = signTokenWithRoles(ISSUER, "reader_usr", 3600_000,
        List.of("reader"));

    HttpResponse resp = enforcer.validateWithRoles(
        request().withPath("/api/test").withHeader("Authorization", "Bearer " + token),
        "admin");
    assertNotNull(resp);
    assertEquals(403, resp.getStatusCode());
  }

  @Test
  void tokenWithWriterRole_passesWriterAdminCheck() {
    TokenEnforcer enforcer = enabledEnforcer("");
    String token = signTokenWithRoles(ISSUER, "writer_usr", 3600_000,
        Arrays.asList("writer", "reader"));

    assertNull(enforcer.validateWithRoles(
        request().withPath("/api/test").withHeader("Authorization", "Bearer " + token),
        "writer", "admin"));
  }

  @Test
  void tokenWithWriterRole_failsAdminOnlyCheck() {
    TokenEnforcer enforcer = enabledEnforcer("");
    String token = signTokenWithRoles(ISSUER, "writer_usr", 3600_000,
        Arrays.asList("writer", "reader"));

    HttpResponse resp = enforcer.validateWithRoles(
        request().withPath("/api/test").withHeader("Authorization", "Bearer " + token),
        "admin");
    assertNotNull(resp);
    assertEquals(403, resp.getStatusCode());
  }

  @Test
  void tokenWithNoRoles_failsAnyRoleCheck() {
    TokenEnforcer enforcer = enabledEnforcer("");
    String token = signToken(ISSUER, "noroles_usr", 3600_000);

    HttpResponse resp = enforcer.validateWithRoles(
        request().withPath("/api/test").withHeader("Authorization", "Bearer " + token),
        "reader", "writer", "admin");
    assertNotNull(resp);
    assertEquals(403, resp.getStatusCode());
  }

  @Test
  void tokenWithNoRoles_passesNoRoleRequirement() {
    TokenEnforcer enforcer = enabledEnforcer("");
    String token = signToken(ISSUER, "noroles_usr", 3600_000);

    assertNull(enforcer.validateWithRoles(
        request().withPath("/api/test").withHeader("Authorization", "Bearer " + token)));
  }

  @Test
  void disabledEnforcer_skipsRoleCheck() {
    TokenEnforcer enforcer = disabledEnforcer();
    assertNull(enforcer.validateWithRoles(
        request().withPath("/api/test"), "admin"));
  }

  @Test
  void forbiddenResponse_hasWwwAuthenticateHeader() {
    TokenEnforcer enforcer = enabledEnforcer("");
    String token = signTokenWithRoles(ISSUER, "reader_usr", 3600_000,
        List.of("reader"));

    HttpResponse resp = enforcer.validateWithRoles(
        request().withPath("/api/test").withHeader("Authorization", "Bearer " + token),
        "admin");
    assertNotNull(resp);
    String wwwAuth = resp.getFirstHeader("WWW-Authenticate");
    assertNotNull(wwwAuth);
    assertTrue(wwwAuth.contains("insufficient_scope"));
  }

  // ---- configurable roles-claim path ----

  private TokenEnforcer enforcerWithClaimPath(String claimPath) {
    return new TokenEnforcer(true, "", localKeySource, claimPath, defaultRolesByMethod());
  }

  private static Map<String, String[]> defaultRolesByMethod() {
    Map<String, String[]> map = new LinkedHashMap<>();
    map.put("GET", new String[]{"reader", "writer", "admin"});
    map.put("POST", new String[]{"writer", "admin"});
    map.put("PUT", new String[]{"writer", "admin"});
    map.put("PATCH", new String[]{"writer", "admin"});
    map.put("DELETE", new String[]{"admin"});
    return map;
  }

  private String signTokenWithClaim(String issuer, String subject, long expiresInMs,
      String topLevelKey, Serializable value) {
    long nowSeconds = System.currentTimeMillis() / 1000;
    Map<String, Serializable> claims = new LinkedHashMap<>();
    claims.put("iss", issuer);
    claims.put("sub", subject);
    claims.put("iat", nowSeconds);
    claims.put("exp", nowSeconds + expiresInMs / 1000);
    claims.put("jti", TSID.Factory.getTsid().toString());
    claims.put(topLevelKey, value);
    return JwtKeyProvider.getInstance().signJwt(claims);
  }

  @Test
  void rolesClaimPath_topLevelGroups_extractsRoles() {
    TokenEnforcer enforcer = enforcerWithClaimPath("groups");
    String token = signTokenWithClaim(ISSUER, "u", 3600_000, "groups",
        (Serializable) Arrays.asList("admin", "extra"));

    assertNull(enforcer.validateWithRoles(
        request().withPath("/api/test").withHeader("Authorization", "Bearer " + token),
        "admin"));
  }

  @Test
  void rolesClaimPath_realmAccessRoles_extractsRoles() {
    TokenEnforcer enforcer = enforcerWithClaimPath("realm_access.roles");
    String token = signTokenWithRoles(ISSUER, "u", 3600_000, List.of("writer"));

    assertNull(enforcer.validateWithRoles(
        request().withPath("/api/test").withHeader("Authorization", "Bearer " + token),
        "writer", "admin"));
  }

  @Test
  void rolesClaimPath_deeplyNested_extractsRoles() {
    TokenEnforcer enforcer =
        enforcerWithClaimPath("resource_access.my-client.roles");

    LinkedHashMap<String, Serializable> roles = new LinkedHashMap<>();
    roles.put("roles", (Serializable) List.of("admin"));
    LinkedHashMap<String, Serializable> resourceAccess = new LinkedHashMap<>();
    resourceAccess.put("my-client", roles);

    String token = signTokenWithClaim(ISSUER, "u", 3600_000,
        "resource_access", resourceAccess);

    assertNull(enforcer.validateWithRoles(
        request().withPath("/api/test").withHeader("Authorization", "Bearer " + token),
        "admin"));
  }

  @Test
  void rolesClaimPath_missingIntermediate_returns403() {
    TokenEnforcer enforcer = enforcerWithClaimPath("resource_access.my-client.roles");
    // No resource_access claim at all
    String token = signToken(ISSUER, "u", 3600_000);

    HttpResponse resp = enforcer.validateWithRoles(
        request().withPath("/api/test").withHeader("Authorization", "Bearer " + token),
        "admin");
    assertNotNull(resp);
    assertEquals(403, resp.getStatusCode());
  }

  @Test
  void rolesClaimPath_leafIsNotArray_returns403() {
    TokenEnforcer enforcer = enforcerWithClaimPath("groups");
    String token = signTokenWithClaim(ISSUER, "u", 3600_000, "groups",
        "not-an-array");

    HttpResponse resp = enforcer.validateWithRoles(
        request().withPath("/api/test").withHeader("Authorization", "Bearer " + token),
        "admin");
    assertNotNull(resp);
    assertEquals(403, resp.getStatusCode());
  }

  @Test
  void rolesClaimPath_configured_ignoresDefaultRealmAccessFallback() {
    // Token DOES have realm_access.roles=[admin], but the configured path is "groups".
    // With a configured path, the default fallback chain MUST NOT kick in.
    TokenEnforcer enforcer = enforcerWithClaimPath("groups");
    String token = signTokenWithRoles(ISSUER, "u", 3600_000, List.of("admin"));

    HttpResponse resp = enforcer.validateWithRoles(
        request().withPath("/api/test").withHeader("Authorization", "Bearer " + token),
        "admin");
    assertNotNull(resp);
    assertEquals(403, resp.getStatusCode());
  }

  @Test
  void rolesClaimPath_unset_fallsBackToRealmAccessThenTopLevelRoles() {
    // Path unset: should still find roles at realm_access.roles
    TokenEnforcer enforcer = enabledEnforcer("");
    String token = signTokenWithRoles(ISSUER, "u", 3600_000, List.of("reader"));

    assertNull(enforcer.validateWithRoles(
        request().withPath("/api/test").withHeader("Authorization", "Bearer " + token),
        "reader"));

    // And to top-level "roles" when realm_access is absent
    String token2 = signTokenWithClaim(ISSUER, "u", 3600_000, "roles",
        (Serializable) List.of("reader"));

    assertNull(enforcer.validateWithRoles(
        request().withPath("/api/test").withHeader("Authorization", "Bearer " + token2),
        "reader"));
  }

  // ---- per-method ROLES_* ----

  private TokenEnforcer enforcerWithRolesByMethod(Map<String, String[]> rolesByMethod) {
    return new TokenEnforcer(true, "", localKeySource, "", rolesByMethod);
  }

  @Test
  void validateForRequest_GET_appliesGetRoles() {
    TokenEnforcer enforcer = enforcerWithRolesByMethod(defaultRolesByMethod());
    String token = signTokenWithRoles(ISSUER, "u", 3600_000, List.of("reader"));

    assertNull(enforcer.validateForRequest(
        request().withMethod("GET").withPath("/x")
            .withHeader("Authorization", "Bearer " + token)));
  }

  @Test
  void validateForRequest_POST_appliesPostRoles_rejectsReader() {
    TokenEnforcer enforcer = enforcerWithRolesByMethod(defaultRolesByMethod());
    String token = signTokenWithRoles(ISSUER, "u", 3600_000, List.of("reader"));

    HttpResponse resp = enforcer.validateForRequest(
        request().withMethod("POST").withPath("/x")
            .withHeader("Authorization", "Bearer " + token));
    assertNotNull(resp);
    assertEquals(403, resp.getStatusCode());
  }

  @Test
  void validateForRequest_DELETE_appliesDeleteRoles_rejectsWriter() {
    TokenEnforcer enforcer = enforcerWithRolesByMethod(defaultRolesByMethod());
    String token = signTokenWithRoles(ISSUER, "u", 3600_000, List.of("writer"));

    HttpResponse resp = enforcer.validateForRequest(
        request().withMethod("DELETE").withPath("/x")
            .withHeader("Authorization", "Bearer " + token));
    assertNotNull(resp);
    assertEquals(403, resp.getStatusCode());
  }

  @Test
  void validateForRequest_emptyMethodConfig_skipsRoleCheck() {
    // ROLES_GET="" semantics: no role check, signature/expiry only.
    Map<String, String[]> map = defaultRolesByMethod();
    map.put("GET", new String[0]);
    TokenEnforcer enforcer = enforcerWithRolesByMethod(map);

    // Token has no roles at all — would normally fail under default GET roles.
    String token = signToken(ISSUER, "u", 3600_000);

    assertNull(enforcer.validateForRequest(
        request().withMethod("GET").withPath("/x")
            .withHeader("Authorization", "Bearer " + token)));
  }

  @Test
  void validateForRequest_customAdminOnlyGet_rejectsReader() {
    Map<String, String[]> map = defaultRolesByMethod();
    map.put("GET", new String[]{"admin"});
    TokenEnforcer enforcer = enforcerWithRolesByMethod(map);

    String token = signTokenWithRoles(ISSUER, "u", 3600_000, List.of("reader"));

    HttpResponse resp = enforcer.validateForRequest(
        request().withMethod("GET").withPath("/x")
            .withHeader("Authorization", "Bearer " + token));
    assertNotNull(resp);
    assertEquals(403, resp.getStatusCode());
  }

  @Test
  void validateForRequest_methodNotInMap_skipsRoleCheck() {
    // Unknown method (HEAD/OPTIONS/etc.) falls through to no-role-check.
    TokenEnforcer enforcer = enforcerWithRolesByMethod(defaultRolesByMethod());
    String token = signToken(ISSUER, "u", 3600_000);

    assertNull(enforcer.validateForRequest(
        request().withMethod("HEAD").withPath("/x")
            .withHeader("Authorization", "Bearer " + token)));
  }

  @Test
  void validateForRequest_disabledEnforcer_passesAnyMethod() {
    TokenEnforcer enforcer = disabledEnforcer();
    assertNull(enforcer.validateForRequest(
        request().withMethod("DELETE").withPath("/x")));
  }

  // ---- branch coverage fill-ins ----

  @Test
  void publicConstructor_disabled_isValidAndAlwaysPasses() {
    // 3-arg public constructor with enabled=false takes the short-circuit path in the ctor.
    TokenEnforcer enforcer = new TokenEnforcer(false, "", "");
    assertNull(enforcer.validate(request().withPath("/anything")));
    assertNull(enforcer.validateForRequest(request().withMethod("GET").withPath("/x")));
  }

  @Test
  void publicConstructor_enabled_noJwks_noIssuer_usesBuiltInKeys() {
    // enabled=true, both jwks and issuer empty → falls back to built-in JwtKeyProvider keys.
    TokenEnforcer enforcer = new TokenEnforcer(true, "", "");
    String token = signToken(ISSUER, "u", 3600_000);
    assertNull(enforcer.validate(
        request().withPath("/x").withHeader("Authorization", "Bearer " + token)));
  }

  @Test
  void publicConstructor_enabled_malformedJwksUri_yieldsInitError_401OnValidate() {
    // Force buildKeySource → new URL("not a url") to throw MalformedURLException, becoming
    // the initError; every validate then returns 401 "misconfigured".
    TokenEnforcer enforcer = new TokenEnforcer(true, "", "not a url");
    HttpResponse resp = enforcer.validate(
        request().withPath("/x").withHeader("Authorization", "Bearer xyz"));
    assertNotNull(resp);
    assertEquals(401, resp.getStatusCode());
    assertTrue(resp.getBodyAsString().contains("misconfigured"));
  }

  @Test
  void publicConstructor_nullIssuerAndJwks_areCoercedToEmpty() {
    // Passing null issuer and jwks exercises the null-to-empty coercion branches in the
    // full-control constructor. Casts disambiguate against the JWKSource-taking overload.
    TokenEnforcer enforcer = new TokenEnforcer(
        true, (String) null, (String) null, null, defaultRolesByMethod());
    String token = signToken(ISSUER, "u", 3600_000);
    assertNull(enforcer.validate(
        request().withPath("/x").withHeader("Authorization", "Bearer " + token)));
  }

  @Test
  void extractBearerToken_missingHeader_isMissingOrMalformed401() {
    TokenEnforcer enforcer = enabledEnforcer("");
    HttpResponse resp = enforcer.validate(request().withPath("/x"));
    assertNotNull(resp);
    assertEquals(401, resp.getStatusCode());
    assertTrue(resp.getBodyAsString().contains("Missing or malformed"));
  }

  @Test
  void extractBearerToken_headerWithoutBearerPrefix_isMissingOrMalformed401() {
    TokenEnforcer enforcer = enabledEnforcer("");
    HttpResponse resp = enforcer.validate(
        request().withPath("/x").withHeader("Authorization", "Basic dXNlcjpwYXNz"));
    assertNotNull(resp);
    assertEquals(401, resp.getStatusCode());
  }

  @Test
  void extractBearerToken_bearerPrefixCaseInsensitive_isAccepted() {
    // regionMatches ignoreCase=true — lowercase "bearer " should be honoured.
    TokenEnforcer enforcer = enabledEnforcer("");
    String token = signToken(ISSUER, "u", 3600_000);
    assertNull(enforcer.validate(
        request().withPath("/x").withHeader("Authorization", "bearer " + token)));
  }

  @Test
  void extractBearerToken_emptyToken_returns401EmptyBearerToken() {
    TokenEnforcer enforcer = enabledEnforcer("");
    HttpResponse resp = enforcer.validate(
        request().withPath("/x").withHeader("Authorization", "Bearer "));
    assertNotNull(resp);
    assertEquals(401, resp.getStatusCode());
    assertTrue(resp.getBodyAsString().contains("Empty bearer token"));
  }

  @Test
  void validateForRequest_lowerCaseMethod_isNormalizedToUpper() {
    TokenEnforcer enforcer = enforcerWithRolesByMethod(defaultRolesByMethod());
    String token = signTokenWithRoles(ISSUER, "u", 3600_000, List.of("admin"));
    HttpRequest req = request().withMethod("get").withPath("/x")
        .withHeader("Authorization", "Bearer " + token);
    assertNull(enforcer.validateForRequest(req));
  }

  @Test
  void extractRoles_customRolesClaimPath_navigatesNestedObject() {
    // rolesClaimPath = "resource_access.my-client.roles"
    Map<String, String[]> roles = new LinkedHashMap<>();
    roles.put("GET", new String[]{"gadmin"});
    TokenEnforcer enforcer = new TokenEnforcer(
        true, "", localKeySource, "resource_access.my-client.roles", roles);

    long nowSeconds = System.currentTimeMillis() / 1000;
    Map<String, Serializable> claims = new LinkedHashMap<>();
    claims.put("iss", ISSUER);
    claims.put("sub", "u");
    claims.put("iat", nowSeconds);
    claims.put("exp", nowSeconds + 3600);
    LinkedHashMap<String, Serializable> myClient = new LinkedHashMap<>();
    myClient.put("roles", (Serializable) List.of("gadmin"));
    LinkedHashMap<String, Serializable> resourceAccess = new LinkedHashMap<>();
    resourceAccess.put("my-client", myClient);
    claims.put("resource_access", resourceAccess);
    String token = JwtKeyProvider.getInstance().signJwt(claims);

    assertNull(enforcer.validateForRequest(request().withMethod("GET").withPath("/x")
        .withHeader("Authorization", "Bearer " + token)));
  }

  @Test
  void extractRolesAtPath_missingSegment_yieldsForbidden() {
    Map<String, String[]> roles = new LinkedHashMap<>();
    roles.put("GET", new String[]{"admin"});
    TokenEnforcer enforcer = new TokenEnforcer(
        true, "", localKeySource, "resource_access.not-there.roles", roles);
    String token = signTokenWithRoles(ISSUER, "u", 3600_000, List.of("admin"));
    HttpResponse resp = enforcer.validateForRequest(request().withMethod("GET").withPath("/x")
        .withHeader("Authorization", "Bearer " + token));
    assertNotNull(resp);
    assertEquals(403, resp.getStatusCode());
  }

  @Test
  void extractRolesAtPath_leafNotAList_yieldsForbidden() {
    Map<String, String[]> roles = new LinkedHashMap<>();
    roles.put("GET", new String[]{"admin"});
    TokenEnforcer enforcer = new TokenEnforcer(
        true, "", localKeySource, "azp", roles);
    long nowSeconds = System.currentTimeMillis() / 1000;
    Map<String, Serializable> claims = new LinkedHashMap<>();
    claims.put("iss", ISSUER);
    claims.put("sub", "u");
    claims.put("iat", nowSeconds);
    claims.put("exp", nowSeconds + 3600);
    claims.put("azp", "some-client");
    String token = JwtKeyProvider.getInstance().signJwt(claims);
    HttpResponse resp = enforcer.validateForRequest(request().withMethod("GET").withPath("/x")
        .withHeader("Authorization", "Bearer " + token));
    assertNotNull(resp);
    assertEquals(403, resp.getStatusCode());
  }

  @Test
  void extractRolesAtPath_cursorNotAMap_yieldsForbidden() {
    // path traverses into a scalar mid-way — cursor becomes non-Map, returns emptySet
    Map<String, String[]> roles = new LinkedHashMap<>();
    roles.put("GET", new String[]{"admin"});
    TokenEnforcer enforcer = new TokenEnforcer(
        true, "", localKeySource, "azp.deeper.field", roles);
    long nowSeconds = System.currentTimeMillis() / 1000;
    Map<String, Serializable> claims = new LinkedHashMap<>();
    claims.put("iss", ISSUER);
    claims.put("sub", "u");
    claims.put("iat", nowSeconds);
    claims.put("exp", nowSeconds + 3600);
    claims.put("azp", "some-client");
    String token = JwtKeyProvider.getInstance().signJwt(claims);
    HttpResponse resp = enforcer.validateForRequest(request().withMethod("GET").withPath("/x")
        .withHeader("Authorization", "Bearer " + token));
    assertNotNull(resp);
    assertEquals(403, resp.getStatusCode());
  }

  @Test
  void extractRolesAtPath_leafListWithMixedTypes_keepsStringsOnly() {
    // ensures the non-String branch inside the leaf loop is exercised
    Map<String, String[]> roles = new LinkedHashMap<>();
    roles.put("GET", new String[]{"admin"});
    TokenEnforcer enforcer = new TokenEnforcer(
        true, "", localKeySource, "groups", roles);
    long nowSeconds = System.currentTimeMillis() / 1000;
    Map<String, Serializable> claims = new LinkedHashMap<>();
    claims.put("iss", ISSUER);
    claims.put("sub", "u");
    claims.put("iat", nowSeconds);
    claims.put("exp", nowSeconds + 3600);
    java.util.ArrayList<Serializable> groups = new java.util.ArrayList<>();
    groups.add("admin");
    groups.add(42);
    claims.put("groups", groups);
    String token = JwtKeyProvider.getInstance().signJwt(claims);
    // "admin" is still a match despite the numeric noise → 200/null.
    assertNull(enforcer.validateForRequest(request().withMethod("GET").withPath("/x")
        .withHeader("Authorization", "Bearer " + token)));
  }

  @Test
  void extractRolesAtPath_missingLeaf_thenTopLevelRolesFallback_notUsed_becausePathConfigured() {
    // When rolesClaimPath is set, the fallback to top-level `roles` is NOT tried.
    Map<String, String[]> roles = new LinkedHashMap<>();
    roles.put("GET", new String[]{"admin"});
    TokenEnforcer enforcer = new TokenEnforcer(
        true, "", localKeySource, "resource_access.not-there.roles", roles);
    long nowSeconds = System.currentTimeMillis() / 1000;
    Map<String, Serializable> claims = new LinkedHashMap<>();
    claims.put("iss", ISSUER);
    claims.put("sub", "u");
    claims.put("iat", nowSeconds);
    claims.put("exp", nowSeconds + 3600);
    claims.put("roles", (Serializable) List.of("admin"));
    String token = JwtKeyProvider.getInstance().signJwt(claims);
    HttpResponse resp = enforcer.validateForRequest(request().withMethod("GET").withPath("/x")
        .withHeader("Authorization", "Bearer " + token));
    assertNotNull(resp);
    assertEquals(403, resp.getStatusCode());
  }

  @Test
  void readTopLevelRoles_fallback_kicksIn_whenRealmAccessAbsent() {
    // No rolesClaimPath, no realm_access; token has top-level "roles" claim.
    Map<String, String[]> roles = new LinkedHashMap<>();
    roles.put("GET", new String[]{"admin"});
    TokenEnforcer enforcer = new TokenEnforcer(true, "", localKeySource, "", roles);
    long nowSeconds = System.currentTimeMillis() / 1000;
    Map<String, Serializable> claims = new LinkedHashMap<>();
    claims.put("iss", ISSUER);
    claims.put("sub", "u");
    claims.put("iat", nowSeconds);
    claims.put("exp", nowSeconds + 3600);
    claims.put("roles", (Serializable) List.of("admin"));
    String token = JwtKeyProvider.getInstance().signJwt(claims);
    assertNull(enforcer.validateForRequest(request().withMethod("GET").withPath("/x")
        .withHeader("Authorization", "Bearer " + token)));
  }

  @Test
  void readRealmAccessRoles_missingClaim_returnsEmpty_leadsTo403WhenRoleRequired() {
    Map<String, String[]> roles = new LinkedHashMap<>();
    roles.put("GET", new String[]{"admin"});
    TokenEnforcer enforcer = new TokenEnforcer(true, "", localKeySource, "", roles);
    // Token with no realm_access, no top-level roles.
    String token = signToken(ISSUER, "u", 3600_000);
    HttpResponse resp = enforcer.validateForRequest(request().withMethod("GET").withPath("/x")
        .withHeader("Authorization", "Bearer " + token));
    assertNotNull(resp);
    assertEquals(403, resp.getStatusCode());
  }

  @Test
  void readRealmAccessRoles_wrongTypeInList_isFiltered() {
    Map<String, String[]> roles = new LinkedHashMap<>();
    roles.put("GET", new String[]{"admin"});
    TokenEnforcer enforcer = new TokenEnforcer(true, "", localKeySource, "", roles);
    long nowSeconds = System.currentTimeMillis() / 1000;
    Map<String, Serializable> claims = new LinkedHashMap<>();
    claims.put("iss", ISSUER);
    claims.put("sub", "u");
    claims.put("iat", nowSeconds);
    claims.put("exp", nowSeconds + 3600);
    java.util.ArrayList<Serializable> rolesList = new java.util.ArrayList<>();
    rolesList.add("admin");
    rolesList.add(123);
    LinkedHashMap<String, Serializable> realmAccess = new LinkedHashMap<>();
    realmAccess.put("roles", rolesList);
    claims.put("realm_access", realmAccess);
    String token = JwtKeyProvider.getInstance().signJwt(claims);
    // "admin" still matches; non-string element ignored, no crash.
    assertNull(enforcer.validateForRequest(request().withMethod("GET").withPath("/x")
        .withHeader("Authorization", "Bearer " + token)));
  }

  @Test
  void readRealmAccessRoles_rolesNotAList_isIgnored_leadsTo403() {
    Map<String, String[]> roles = new LinkedHashMap<>();
    roles.put("GET", new String[]{"admin"});
    TokenEnforcer enforcer = new TokenEnforcer(true, "", localKeySource, "", roles);
    long nowSeconds = System.currentTimeMillis() / 1000;
    Map<String, Serializable> claims = new LinkedHashMap<>();
    claims.put("iss", ISSUER);
    claims.put("sub", "u");
    claims.put("iat", nowSeconds);
    claims.put("exp", nowSeconds + 3600);
    LinkedHashMap<String, Serializable> realmAccess = new LinkedHashMap<>();
    realmAccess.put("roles", "not-a-list");
    claims.put("realm_access", realmAccess);
    String token = JwtKeyProvider.getInstance().signJwt(claims);
    HttpResponse resp = enforcer.validateForRequest(request().withMethod("GET").withPath("/x")
        .withHeader("Authorization", "Bearer " + token));
    assertNotNull(resp);
    assertEquals(403, resp.getStatusCode());
  }

  @Test
  void validateWithRoles_emptyRequiredRoles_skipsRoleCheck() {
    // requiredRoles.length == 0 short-circuits checkRoles to null.
    TokenEnforcer enforcer = enabledEnforcer("");
    String token = signToken(ISSUER, "u", 3600_000);
    assertNull(enforcer.validateWithRoles(
        request().withPath("/x").withHeader("Authorization", "Bearer " + token)));
  }

  @Test
  void singletonGetInstance_returnsSameReference() {
    assertSame(TokenEnforcer.getInstance(), TokenEnforcer.getInstance());
  }

  @Test
  void publicSingleton_disabledByDefault_letsRequestsThrough() {
    // No env vars set in the test runner → default is enabled=false → validate returns null.
    assertNull(TokenEnforcer.getInstance().validate(request().withPath("/x")));
  }

  @Test
  void twoArgConstructor_validatesSuccessfully_whenTokenIsGood() {
    // Uses the 3-arg constructor path, but with iss empty and jwks empty → built-in keys.
    TokenEnforcer enforcer = new TokenEnforcer(true, "", "");
    String token = signToken(ISSUER, "sub", 3600_000);
    assertNull(enforcer.validate(
        request().withPath("/x").withHeader("Authorization", "Bearer " + token)));
  }
}
