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

class TokenEnforcerTests {

  private static final String ISSUER = "https://mockserver/test";
  private static JWKSource<SecurityContext> LOCAL_KEY_SOURCE;

  @BeforeAll
  static void initKeySource() throws ParseException {
    JWKSet keys = JWKSet.parse(JwtKeyProvider.getInstance().getJwksJson());
    LOCAL_KEY_SOURCE = new ImmutableJWKSet<>(keys);
  }

  private TokenEnforcer enabledEnforcer(String expectedIssuer) {
    return new TokenEnforcer(true, expectedIssuer, LOCAL_KEY_SOURCE);
  }

  private TokenEnforcer disabledEnforcer() {
    return new TokenEnforcer(false, "", LOCAL_KEY_SOURCE);
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
    return new TokenEnforcer(true, "", LOCAL_KEY_SOURCE, claimPath, defaultRolesByMethod());
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
    return new TokenEnforcer(true, "", LOCAL_KEY_SOURCE, "", rolesByMethod);
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
}
