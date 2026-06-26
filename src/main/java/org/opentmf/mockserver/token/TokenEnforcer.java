package org.opentmf.mockserver.token;

import static org.opentmf.mockserver.util.JacksonUtil.writeAsString;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.MediaType;
import org.opentmf.mockserver.model.TokenError;
import org.opentmf.mockserver.util.JacksonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

/**
 * Validates Bearer JWT tokens on incoming requests.
 *
 * <p>Controlled by these configuration knobs (env var or system property, env wins when both
 * present):
 *
 * <ul>
 *   <li>{@code ENFORCE_TOKEN} / {@code enforce.token} -- {@code true} to enable (default {@code
 *       false})
 *   <li>{@code TOKEN_ISSUER} / {@code token.issuer} -- expected {@code iss} claim; also used for
 *       OIDC auto-discovery of the JWKS URI when {@code JWKS_URI} is not set
 *   <li>{@code JWKS_URI} / {@code jwks.uri} -- explicit JWKS endpoint (takes precedence over
 *       discovery)
 *   <li>{@code ROLES_CLAIM_PATH} / {@code roles.claim.path} -- dotted JSON path to the roles array
 *       inside the token (e.g. {@code realm_access.roles}, {@code resource_access.my-client.roles},
 *       {@code groups}). When unset, the enforcer tries {@code realm_access.roles} and falls back
 *       to a top-level {@code roles} claim.
 *   <li>{@code ROLES_GET} / {@code ROLES_POST} / {@code ROLES_PUT} / {@code ROLES_PATCH} /
 *       {@code ROLES_DELETE} -- comma-separated list of roles required for each HTTP method.
 *       Defaults preserve the historical behaviour ({@code reader,writer,admin} for GET;
 *       {@code writer,admin} for POST/PUT/PATCH; {@code admin} for DELETE). An empty value
 *       (e.g. {@code ROLES_GET=}) disables the role check for that method while still validating
 *       signature and expiry.
 * </ul>
 *
 * <p>When neither {@code JWKS_URI} nor {@code TOKEN_ISSUER} is set, the built-in mock keys from
 * {@link JwtKeyProvider} are used for signature verification.
 */
public final class TokenEnforcer {

  private static final Logger LOG = LoggerFactory.getLogger(TokenEnforcer.class);

  private static final String DEFAULT_ROLES_GET = "reader,writer,admin";
  private static final String DEFAULT_ROLES_POST = "writer,admin";
  private static final String DEFAULT_ROLES_PUT = "writer,admin";
  private static final String DEFAULT_ROLES_PATCH = "writer,admin";
  private static final String DEFAULT_ROLES_DELETE = "admin";

  private static volatile TokenEnforcer instance;

  private final boolean enabled;
  private final String expectedIssuer;
  private final String rolesClaimPath;
  private final Map<String, String[]> rolesByMethod;
  private final ConfigurableJWTProcessor<SecurityContext> jwtProcessor;
  private final String initError;

  /** Production singleton. Reads config from env vars / system properties. */
  public static TokenEnforcer getInstance() {
    if (instance == null) {
      synchronized (TokenEnforcer.class) {
        if (instance == null) {
          instance = new TokenEnforcer();
        }
      }
    }
    return instance;
  }

  private TokenEnforcer() {
    this(
        Boolean.parseBoolean(resolve("ENFORCE_TOKEN", "enforce.token", "false")),
        resolve("TOKEN_ISSUER", "token.issuer", ""),
        resolve("JWKS_URI", "jwks.uri", ""),
        resolve("ROLES_CLAIM_PATH", "roles.claim.path", ""),
        rolesByMethodFromEnv());
  }

  private static Map<String, String[]> rolesByMethodFromEnv() {
    Map<String, String[]> map = new LinkedHashMap<>();
    map.put("GET", parseRoles(resolve("ROLES_GET", "roles.get", DEFAULT_ROLES_GET)));
    map.put("POST", parseRoles(resolve("ROLES_POST", "roles.post", DEFAULT_ROLES_POST)));
    map.put("PUT", parseRoles(resolve("ROLES_PUT", "roles.put", DEFAULT_ROLES_PUT)));
    map.put("PATCH", parseRoles(resolve("ROLES_PATCH", "roles.patch", DEFAULT_ROLES_PATCH)));
    map.put("DELETE", parseRoles(resolve("ROLES_DELETE", "roles.delete", DEFAULT_ROLES_DELETE)));
    return map;
  }

  private static String[] parseRoles(String csv) {
    if (csv == null || csv.isEmpty()) {
      return new String[0];
    }
    String[] parts = csv.split(",");
    List<String> out = new java.util.ArrayList<>(parts.length);
    for (String part : parts) {
      String trimmed = part.trim();
      if (!trimmed.isEmpty()) {
        out.add(trimmed);
      }
    }
    return out.toArray(new String[0]);
  }

  /**
   * Creates a TokenEnforcer with explicit configuration.
   *
   * @param enabled whether token enforcement is active
   * @param issuerConfig expected {@code iss} claim (empty string to skip check); also used for OIDC
   *     discovery when {@code jwksUriConfig} is empty
   * @param jwksUriConfig explicit JWKS endpoint (empty string to fall back to discovery or built-in
   *     keys)
   */
  public TokenEnforcer(boolean enabled, String issuerConfig, String jwksUriConfig) {
    this(enabled, issuerConfig, jwksUriConfig, "", rolesByMethodFromEnv());
  }

  /**
   * Full-control constructor used by the no-arg variant. Tests should prefer the JWKSource-backed
   * constructor below.
   */
  public TokenEnforcer(
      boolean enabled,
      String issuerConfig,
      String jwksUriConfig,
      String rolesClaimPathConfig,
      Map<String, String[]> rolesByMethod) {
    this.enabled = enabled;
    if (issuerConfig == null) issuerConfig = "";
    if (jwksUriConfig == null) jwksUriConfig = "";
    this.rolesClaimPath =
        (rolesClaimPathConfig == null || rolesClaimPathConfig.isEmpty())
            ? null
            : rolesClaimPathConfig;
    this.rolesByMethod = Collections.unmodifiableMap(rolesByMethod);

    if (!enabled) {
      LOG.info("Token enforcement is DISABLED");
      this.expectedIssuer = null;
      this.jwtProcessor = null;
      this.initError = null;
      return;
    }

    this.expectedIssuer = issuerConfig.isEmpty() ? null : issuerConfig;

    String error = null;
    ConfigurableJWTProcessor<SecurityContext> proc = null;
    try {
      JWKSource<SecurityContext> keySource = buildKeySource(jwksUriConfig, issuerConfig);
      proc = createProcessor(keySource);
    } catch (Exception e) {
      error = e.getMessage();
      LOG.error("TokenEnforcer initialisation failed: {}", error, e);
    }
    this.jwtProcessor = proc;
    this.initError = error;

    LOG.info(
        "Token enforcement is ENABLED (issuer={}, jwks={}, rolesClaimPath={}, rolesByMethod={})",
        expectedIssuer != null ? expectedIssuer : "<any>",
        jwksUriConfig.isEmpty()
            ? (issuerConfig.isEmpty() ? "<built-in>" : "<discovered>")
            : jwksUriConfig,
        rolesClaimPath != null ? rolesClaimPath : "<default: realm_access.roles | roles>",
        describeRolesByMethod());
  }

  /**
   * Package-private constructor for testing with an explicit key source. Bypasses OIDC discovery
   * entirely.
   */
  TokenEnforcer(boolean enabled, String expectedIssuer, JWKSource<SecurityContext> keySource) {
    this(enabled, expectedIssuer, keySource, "", rolesByMethodFromEnv());
  }

  /**
   * Package-private constructor for testing with an explicit key source plus role config. Bypasses
   * OIDC discovery entirely.
   */
  TokenEnforcer(
      boolean enabled,
      String expectedIssuer,
      JWKSource<SecurityContext> keySource,
      String rolesClaimPathConfig,
      Map<String, String[]> rolesByMethod) {
    this.enabled = enabled;
    this.expectedIssuer =
        (expectedIssuer == null || expectedIssuer.isEmpty()) ? null : expectedIssuer;
    this.rolesClaimPath =
        (rolesClaimPathConfig == null || rolesClaimPathConfig.isEmpty())
            ? null
            : rolesClaimPathConfig;
    this.rolesByMethod = Collections.unmodifiableMap(rolesByMethod);

    if (!enabled) {
      this.jwtProcessor = null;
      this.initError = null;
      return;
    }

    this.jwtProcessor = createProcessor(keySource);
    this.initError = null;
  }

  @SuppressWarnings("deprecation")
  private static ConfigurableJWTProcessor<SecurityContext> createProcessor(
      JWKSource<SecurityContext> keySource) {
    DefaultJWTProcessor<SecurityContext> proc = new DefaultJWTProcessor<>();
    proc.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource));
    proc.setJWTClaimsSetVerifier(new com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier<>());
    return proc;
  }

  /**
   * Validates the Bearer token on the request (signature, expiration, issuer). Does not check
   * roles.
   *
   * @return {@code null} if validation passes (or enforcement is disabled); an HTTP 401 response
   *     otherwise.
   */
  public HttpResponse validate(HttpRequest request) {
    return validateWithRoles(request);
  }

  /**
   * Validates the Bearer token and resolves the required roles from the configured per-method map
   * ({@code ROLES_GET}, {@code ROLES_POST}, ...) based on {@link HttpRequest#getMethod()}. An empty
   * configured list for the method means signature/expiry/issuer are still checked but no role
   * check is performed.
   *
   * @return {@code null} if validation passes (or enforcement is disabled); an HTTP 401 or 403
   *     response otherwise.
   */
  public HttpResponse validateForRequest(HttpRequest request) {
    String method = request.getMethod().getValue();
    String[] required =
        rolesByMethod.getOrDefault(method.toUpperCase(Locale.ROOT), new String[0]);
    return validateWithRoles(request, required);
  }

  /**
   * Validates the Bearer token and optionally checks that the token carries at least one of the
   * specified roles. Roles are extracted from {@code ROLES_CLAIM_PATH} when configured, otherwise
   * from the Keycloak {@code realm_access.roles} claim first, falling back to a top-level
   * {@code roles} claim.
   *
   * @param request the incoming HTTP request
   * @param requiredRoles roles of which the token must contain at least one; if empty, role
   *     checking is skipped
   * @return {@code null} if validation passes; an HTTP 401 or 403 response otherwise.
   */
  public HttpResponse validateWithRoles(HttpRequest request, String... requiredRoles) {
    if (!enabled) {
      return null;
    }
    if (initError != null) {
      return unauthorizedResponse("Token enforcer is misconfigured: " + initError);
    }

    String authHeader = request.getFirstHeader("Authorization");
    if (authHeader == null || !authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
      return unauthorizedResponse("Missing or malformed Authorization header");
    }

    String token = authHeader.substring(7).trim();
    if (token.isEmpty()) {
      return unauthorizedResponse("Empty bearer token");
    }

    try {
      JWTClaimsSet claims = jwtProcessor.process(token, null);

      java.util.Date exp = claims.getExpirationTime();
      if (exp != null && new java.util.Date().after(exp)) {
        return unauthorizedResponse("Token has expired");
      }

      if (expectedIssuer != null) {
        String actualIssuer = claims.getIssuer();
        if (!expectedIssuer.equals(actualIssuer)) {
          return unauthorizedResponse(
              "Issuer mismatch: expected \""
                  + expectedIssuer
                  + "\" but got \""
                  + actualIssuer
                  + "\"");
        }
      }

      if (requiredRoles.length > 0) {
        Set<String> tokenRoles = extractRoles(claims);
        Set<String> allowed = new HashSet<>(Arrays.asList(requiredRoles));
        boolean hasRole = false;
        for (String r : tokenRoles) {
          if (allowed.contains(r)) {
            hasRole = true;
            break;
          }
        }
        if (!hasRole) {
          return forbiddenResponse(
              "Insufficient role. Required: " + Arrays.toString(requiredRoles));
        }
      }

      return null;
    } catch (ParseException | BadJOSEException | JOSEException e) {
      LOG.debug("Token validation failed: {}", e.getMessage());
      return unauthorizedResponse(e.getMessage());
    }
  }

  // ---- JWKS source resolution ----

  @SuppressWarnings("deprecation")
  private JWKSource<SecurityContext> buildKeySource(String jwksUri, String issuer) {
    try {
      if (!jwksUri.isEmpty()) {
        LOG.info("Using JWKS from explicit URI: {}", jwksUri);
        return new RemoteJWKSet<>(new URL(jwksUri));
      }

      if (!issuer.isEmpty()) {
        String discovered = discoverJwksUri(issuer);
        LOG.info("Discovered JWKS URI from issuer: {}", discovered);
        return new RemoteJWKSet<>(new URL(discovered));
      }
    } catch (MalformedURLException e) {
      throw new IllegalStateException("Malformed JWKS URL", e);
    }

    LOG.info("Using built-in JWKS from JwtKeyProvider");
    try {
      JWKSet localKeys = JWKSet.parse(JwtKeyProvider.getInstance().getJwksJson());
      return new ImmutableJWKSet<>(localKeys);
    } catch (ParseException e) {
      throw new IllegalStateException("Failed to parse built-in JWKS", e);
    }
  }

  private static String discoverJwksUri(String issuer) {
    String discoveryUrl = issuer + "/.well-known/openid-configuration";
    try {
      HttpURLConnection conn = (HttpURLConnection) new URL(discoveryUrl).openConnection();
      conn.setConnectTimeout(5000);
      conn.setReadTimeout(5000);
      conn.setRequestMethod("GET");

      if (conn.getResponseCode() != 200) {
        throw new IOException("HTTP " + conn.getResponseCode() + " from " + discoveryUrl);
      }

      try (InputStream is = conn.getInputStream()) {
        byte[] bytes = is.readAllBytes();
        JsonNode doc = JacksonUtil.readAsTree(new String(bytes, StandardCharsets.UTF_8));
        JsonNode jwksNode = doc.get("jwks_uri");
        if (jwksNode == null || jwksNode.asText().isEmpty()) {
          throw new IOException("jwks_uri not found in discovery document at " + discoveryUrl);
        }
        return jwksNode.asText();
      }
    } catch (IOException e) {
      throw new IllegalStateException("OIDC discovery failed for " + discoveryUrl, e);
    }
  }

  // ---- role extraction ----

  @SuppressWarnings("unchecked")
  private Set<String> extractRoles(JWTClaimsSet claims) {
    if (rolesClaimPath != null) {
      return extractRolesAtPath(claims, rolesClaimPath);
    }

    Set<String> roles = new HashSet<>();
    try {
      Map<String, Object> realmAccess = claims.getJSONObjectClaim("realm_access");
      if (realmAccess != null) {
        Object rolesObj = realmAccess.get("roles");
        if (rolesObj instanceof List) {
          for (Object r : (List<Object>) rolesObj) {
            if (r instanceof String) {
              roles.add((String) r);
            }
          }
        }
      }
    } catch (ParseException ignored) {
      // claim absent or wrong type
    }

    if (roles.isEmpty()) {
      try {
        List<String> topLevel = claims.getStringListClaim("roles");
        if (topLevel != null) {
          roles.addAll(topLevel);
        }
      } catch (ParseException ignored) {
        // claim absent or wrong type
      }
    }

    return Collections.unmodifiableSet(roles);
  }

  /**
   * Traverses the dotted path against the token's claims. Each segment indexes into a JSON object;
   * the final segment must resolve to a JSON array of strings. Returns an empty set if any segment
   * is missing or the leaf is the wrong shape.
   */
  @SuppressWarnings("unchecked")
  private static Set<String> extractRolesAtPath(JWTClaimsSet claims, String path) {
    String[] segments = path.split("\\.");
    Object cursor = claims.toJSONObject();
    for (int i = 0; i < segments.length; i++) {
      if (!(cursor instanceof Map<?, ?> m)) {
        return Collections.emptySet();
      }
      cursor = ((Map<String, Object>) m).get(segments[i]);
      if (cursor == null) {
        return Collections.emptySet();
      }
    }
    if (!(cursor instanceof List<?> list)) {
      return Collections.emptySet();
    }
    Set<String> roles = new HashSet<>();
    for (Object o : list) {
      if (o instanceof String s) {
        roles.add(s);
      }
    }
    return Collections.unmodifiableSet(roles);
  }

  // ---- helpers ----

  private HttpResponse forbiddenResponse(String description) {
    TokenError error = new TokenError("insufficient_scope", description, "");
    return HttpResponse.response()
        .withStatusCode(403)
        .withContentType(MediaType.APPLICATION_JSON)
        .withHeader(
            "WWW-Authenticate",
            "Bearer error=\"insufficient_scope\"," + " error_description=\"" + description + "\"")
        .withBody(writeAsString(error));
  }

  private HttpResponse unauthorizedResponse(String description) {
    TokenError error = new TokenError("invalid_token", description, "");
    return HttpResponse.response()
        .withStatusCode(401)
        .withContentType(MediaType.APPLICATION_JSON)
        .withHeader(
            "WWW-Authenticate",
            "Bearer error=\"invalid_token\"," + " error_description=\"" + description + "\"")
        .withBody(writeAsString(error));
  }

  private String describeRolesByMethod() {
    StringBuilder sb = new StringBuilder("{");
    boolean first = true;
    for (Map.Entry<String, String[]> e : rolesByMethod.entrySet()) {
      if (!first) {
        sb.append(", ");
      }
      first = false;
      sb.append(e.getKey()).append('=').append(Arrays.toString(e.getValue()));
    }
    return sb.append('}').toString();
  }

  private static String resolve(String envVar, String sysProp, String defaultVal) {
    String val = System.getProperty(sysProp);
    if (val != null) {
      return val;
    }
    val = System.getenv(envVar);
    if (val != null) {
      return val;
    }
    return defaultVal;
  }
}
