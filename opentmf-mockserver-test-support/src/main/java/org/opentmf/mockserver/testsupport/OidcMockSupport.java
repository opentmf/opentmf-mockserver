package org.opentmf.mockserver.testsupport;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import java.io.Serializable;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.mockserver.client.MockServerClient;
import org.mockserver.matchers.Times;
import org.mockserver.model.MediaType;
import org.opentmf.mockserver.token.JwtKeyProvider;

/**
 * JWT minting + JWKS endpoint registration on top of the mockserver's built-in
 * {@link JwtKeyProvider} singleton. Tokens produced here verify against the JWKS this class
 * serves, so a Spring resource-server configured with
 * {@code opentmf.security.jwk-set-uri} pointed at {@link #jwksUri()} accepts them.
 *
 * <p>Configuration is fluent — {@link #realm(String)} and {@link #clientId(String)} set the
 * {@code iss} and {@code azp} claims respectively; defaults are {@link #DEFAULT_REALM} and
 * {@link #DEFAULT_CLIENT_ID}.
 */
public class OidcMockSupport {

  /** Default realm — matches the mockserver's stock Keycloak-mock realm. */
  public static final String DEFAULT_REALM = "realm1";

  /** Default {@code azp} client id. */
  public static final String DEFAULT_CLIENT_ID = "opentmf-mockserver";

  /** Default expiry — one hour. */
  public static final int DEFAULT_EXPIRES_IN_SECONDS = 3600;

  private final MockServerClient client;
  private final String baseUrl;
  private String realm = DEFAULT_REALM;
  private String clientId = DEFAULT_CLIENT_ID;
  private int expiresInSeconds = DEFAULT_EXPIRES_IN_SECONDS;

  OidcMockSupport(MockServerClient client, String baseUrl) {
    this.client = client;
    this.baseUrl = baseUrl;
  }

  /** Set the realm — feeds the {@code iss} claim and the JWKS path. */
  public OidcMockSupport realm(String realm) {
    this.realm = realm;
    return this;
  }

  /** Set the {@code azp} (authorized party) claim and the resource_access map key. */
  public OidcMockSupport clientId(String clientId) {
    this.clientId = clientId;
    return this;
  }

  /** Override the token expiry (default is one hour). */
  public OidcMockSupport expiresInSeconds(int expiresInSeconds) {
    this.expiresInSeconds = expiresInSeconds;
    return this;
  }

  /** Mint a JWT for user {@code "test-user"} with the given roles. */
  public String token(String... roles) {
    return tokenFor("test-user", roles);
  }

  /** Mint a JWT for the given username and roles. */
  public String tokenFor(String username, String... roles) {
    List<String> roleList = Arrays.asList(roles);
    long now = System.currentTimeMillis();
    Map<String, Serializable> claims = new LinkedHashMap<>();
    claims.put("iss", baseUrl + "/realms/" + realm);
    claims.put("sub", "user-" + username);
    claims.put("typ", "Bearer");
    claims.put("azp", clientId);
    claims.put("iat", now / 1000);
    claims.put("exp", now / 1000 + expiresInSeconds);
    claims.put("jti", UUID.randomUUID().toString());
    claims.put("preferred_username", username);

    LinkedHashMap<String, Serializable> realmAccess = new LinkedHashMap<>();
    realmAccess.put("roles", (Serializable) roleList);
    claims.put("realm_access", realmAccess);

    LinkedHashMap<String, Serializable> clientRoles = new LinkedHashMap<>();
    clientRoles.put("roles", (Serializable) roleList);
    LinkedHashMap<String, Serializable> resourceAccess = new LinkedHashMap<>();
    resourceAccess.put(clientId, clientRoles);
    claims.put("resource_access", resourceAccess);

    return JwtKeyProvider.getInstance().signJwt(claims);
  }

  /** Path portion of the JWKS URI: {@code /realms/<realm>/protocol/openid-connect/certs}. */
  public String jwksPath() {
    return "/realms/" + realm + "/protocol/openid-connect/certs";
  }

  /** Full JWKS URI including scheme + host + port. */
  public String jwksUri() {
    return baseUrl + jwksPath();
  }

  /** Raw JWKS JSON as served by {@link JwtKeyProvider}. */
  public String jwksJson() {
    return JwtKeyProvider.getInstance().getJwksJson();
  }

  /**
   * Register a MockServer expectation that serves the JWKS JSON at {@link #jwksPath()}.
   * Called automatically by {@link MockServerSupport#redirectJwks}; call directly if you
   * want the endpoint up without redirecting a Spring property.
   */
  public OidcMockSupport registerJwksEndpoint() {
    client
        .when(request().withMethod("GET").withPath(jwksPath()), Times.unlimited())
        .respond(
            response()
                .withStatusCode(200)
                .withContentType(MediaType.APPLICATION_JSON)
                .withHeader("Cache-Control", "public, max-age=3600")
                .withBody(jwksJson()));
    return this;
  }
}
