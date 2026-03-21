package org.opentmf.mockserver.token;

import static org.opentmf.mockserver.util.JacksonUtil.writeAsString;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.MediaType;
import org.opentmf.mockserver.model.TokenError;
import org.opentmf.mockserver.util.JacksonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates Bearer JWT tokens on incoming requests.
 *
 * <p>Controlled by three configuration knobs (env var or system property):
 * <ul>
 *   <li>{@code ENFORCE_TOKEN} / {@code enforce.token} -- {@code true} to enable (default {@code false})</li>
 *   <li>{@code TOKEN_ISSUER} / {@code token.issuer} -- expected {@code iss} claim; also used for
 *       OIDC auto-discovery of the JWKS URI when {@code JWKS_URI} is not set</li>
 *   <li>{@code JWKS_URI} / {@code jwks.uri} -- explicit JWKS endpoint (takes precedence over discovery)</li>
 * </ul>
 *
 * <p>When neither {@code JWKS_URI} nor {@code TOKEN_ISSUER} is set, the built-in mock keys from
 * {@link JwtKeyProvider} are used for signature verification.
 */
public final class TokenEnforcer {

  private static final Logger LOG = LoggerFactory.getLogger(TokenEnforcer.class);

  private static volatile TokenEnforcer instance;

  private final boolean enabled;
  private final String expectedIssuer;
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
    this(Boolean.parseBoolean(resolve("ENFORCE_TOKEN", "enforce.token", "false")),
        resolve("TOKEN_ISSUER", "token.issuer", ""),
        resolve("JWKS_URI", "jwks.uri", ""));
  }

  /**
   * Creates a TokenEnforcer with explicit configuration.
   *
   * @param enabled       whether token enforcement is active
   * @param issuerConfig  expected {@code iss} claim (empty string to skip check);
   *                      also used for OIDC discovery when {@code jwksUriConfig} is empty
   * @param jwksUriConfig explicit JWKS endpoint (empty string to fall back to discovery or built-in keys)
   */
  public TokenEnforcer(boolean enabled, String issuerConfig, String jwksUriConfig) {
    this.enabled = enabled;
    if (issuerConfig == null) issuerConfig = "";
    if (jwksUriConfig == null) jwksUriConfig = "";

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

    LOG.info("Token enforcement is ENABLED (issuer={}, jwks={})",
        expectedIssuer != null ? expectedIssuer : "<any>",
        jwksUriConfig.isEmpty()
            ? (issuerConfig.isEmpty() ? "<built-in>" : "<discovered>")
            : jwksUriConfig);
  }

  /**
   * Package-private constructor for testing with an explicit key source.
   * Bypasses OIDC discovery entirely.
   */
  TokenEnforcer(boolean enabled, String expectedIssuer,
      JWKSource<SecurityContext> keySource) {
    this.enabled = enabled;
    this.expectedIssuer = (expectedIssuer == null || expectedIssuer.isEmpty())
        ? null : expectedIssuer;

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
    proc.setJWSKeySelector(
        new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource));
    proc.setJWTClaimsSetVerifier(new com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier<>());
    return proc;
  }

  /**
   * Validates the Bearer token on the request (signature, expiration, issuer).
   * Does not check roles.
   *
   * @return {@code null} if validation passes (or enforcement is disabled);
   *         an HTTP 401 response otherwise.
   */
  public HttpResponse validate(HttpRequest request) {
    return validateWithRoles(request);
  }

  /**
   * Validates the Bearer token and optionally checks that the token carries at least one of the
   * specified roles. Roles are extracted from the Keycloak {@code realm_access.roles} claim
   * first, falling back to a top-level {@code roles} claim.
   *
   * @param request       the incoming HTTP request
   * @param requiredRoles roles of which the token must contain at least one;
   *                      if empty, role checking is skipped
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
              "Issuer mismatch: expected \"" + expectedIssuer
                  + "\" but got \"" + actualIssuer + "\"");
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
  private static Set<String> extractRoles(JWTClaimsSet claims) {
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
    } catch (ParseException ignored) { /* claim absent or wrong type */ }

    if (roles.isEmpty()) {
      try {
        List<String> topLevel = claims.getStringListClaim("roles");
        if (topLevel != null) {
          roles.addAll(topLevel);
        }
      } catch (ParseException ignored) { /* claim absent or wrong type */ }
    }

    return Collections.unmodifiableSet(roles);
  }

  // ---- helpers ----

  private HttpResponse forbiddenResponse(String description) {
    TokenError error = new TokenError("insufficient_scope", description, "");
    return HttpResponse.response()
        .withStatusCode(403)
        .withContentType(MediaType.APPLICATION_JSON)
        .withHeader("WWW-Authenticate",
            "Bearer error=\"insufficient_scope\","
                + " error_description=\"" + description + "\"")
        .withBody(writeAsString(error));
  }

  private HttpResponse unauthorizedResponse(String description) {
    TokenError error = new TokenError("invalid_token", description, "");
    return HttpResponse.response()
        .withStatusCode(401)
        .withContentType(MediaType.APPLICATION_JSON)
        .withHeader("WWW-Authenticate",
            "Bearer error=\"invalid_token\","
                + " error_description=\"" + description + "\"")
        .withBody(writeAsString(error));
  }

  private static String resolve(String envVar, String sysProp, String defaultVal) {
    String val = System.getProperty(sysProp);
    if (val != null && !val.isEmpty()) {
      return val;
    }
    val = System.getenv(envVar);
    if (val != null && !val.isEmpty()) {
      return val;
    }
    return defaultVal;
  }
}
