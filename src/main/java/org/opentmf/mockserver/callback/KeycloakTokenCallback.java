package org.opentmf.mockserver.callback;

import static org.opentmf.mockserver.util.JacksonUtil.writeAsString;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.MediaType;
import org.opentmf.mockserver.keycloak.ClientConfig;
import org.opentmf.mockserver.keycloak.KeycloakConfig;
import org.opentmf.mockserver.keycloak.RealmConfig;
import org.opentmf.mockserver.keycloak.UserConfig;
import org.opentmf.mockserver.model.OpenidTokenResponse;
import org.opentmf.mockserver.model.TokenError;
import org.opentmf.mockserver.token.JwtKeyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keycloak-compatible token endpoint callback.
 *
 * <p>Handles {@code POST /realms/{realm}/protocol/openid-connect/token} requests with strict
 * validation of client credentials, grant types and user passwords, producing JWTs with
 * Keycloak-style claims ({@code realm_access}, {@code resource_access},
 * {@code preferred_username}).
 */
public class KeycloakTokenCallback implements ExpectationResponseCallback {

  private static final Logger LOG = LoggerFactory.getLogger(KeycloakTokenCallback.class);

  private static final int EXPIRES_IN_SECONDS = 3600;
  private static final String GRANT_CLIENT_CREDENTIALS = "client_credentials";
  private static final String GRANT_PASSWORD = "password";
  private static final String GRANT_REFRESH_TOKEN = "refresh_token";

  @Override
  public HttpResponse handle(HttpRequest httpRequest) {
    String path = httpRequest.getPath().getValue();
    String realm = extractRealm(path);

    KeycloakConfig config = KeycloakConfig.getInstance();
    Optional<RealmConfig> realmOpt = config.findRealm(realm);
    if (realmOpt.isEmpty()) {
      LOG.warn("Unknown realm: {}", realm);
      return errorResponse(404, "Realm does not exist");
    }
    RealmConfig realmCfg = realmOpt.get();

    Map<String, String> params = parseFormBody(httpRequest);
    String grantType = params.getOrDefault("grant_type", "");

    String clientId = params.getOrDefault("client_id", "");
    String clientSecret = params.getOrDefault("client_secret", "");

    String[] basicAuth = extractBasicAuth(httpRequest);
    if (basicAuth != null) {
      clientId = basicAuth[0];
      clientSecret = basicAuth[1];
    }

    if (clientId.isEmpty()) {
      return tokenError(400, "invalid_request", "Missing client_id");
    }

    Optional<ClientConfig> clientOpt = realmCfg.findClient(clientId);
    if (clientOpt.isEmpty()) {
      return tokenError(401, "invalid_client", "Unknown client: " + clientId);
    }
    ClientConfig clientCfg = clientOpt.get();

    if (!clientCfg.isPublicClient()) {
      String expectedSecret = clientCfg.getClientSecret();
      if (expectedSecret != null && !expectedSecret.equals(clientSecret)) {
        return tokenError(401, "unauthorized_client", "Bad client credentials");
      }
    }

    if (!clientCfg.getAllowedGrantTypes().contains(grantType)) {
      return tokenError(400, "unauthorized_client",
          "Grant type \"" + grantType + "\" not allowed for client " + clientId);
    }

    String baseUrl = config.getBaseUrl();
    String issuer = baseUrl + "/realms/" + realm;

    switch (grantType) {
      case GRANT_CLIENT_CREDENTIALS:
        return handleClientCredentials(realmCfg, clientCfg, issuer);
      case GRANT_PASSWORD:
        return handlePassword(realmCfg, clientCfg, params, issuer);
      case GRANT_REFRESH_TOKEN:
        return handleRefreshToken(realmCfg, clientCfg, params, issuer);
      default:
        return tokenError(400, "unsupported_grant_type",
            "Grant type \"" + grantType + "\" is not supported");
    }
  }

  private HttpResponse handleClientCredentials(RealmConfig realmCfg, ClientConfig clientCfg,
      String issuer) {
    List<String> roles = clientCfg.getServiceAccountRoles() != null
        ? clientCfg.getServiceAccountRoles()
        : realmCfg.getRoles();

    String subject = "service-account-" + clientCfg.getClientId();
    String scope = "openid profile email";

    return buildTokenResponse(issuer, subject, clientCfg.getClientId(), roles, null, scope);
  }

  private HttpResponse handlePassword(RealmConfig realmCfg, ClientConfig clientCfg,
      Map<String, String> params, String issuer) {
    String username = params.getOrDefault("username", "");
    String password = params.getOrDefault("password", "");

    if (username.isEmpty() || password.isEmpty()) {
      return tokenError(400, "invalid_request", "Missing username or password");
    }

    Optional<UserConfig> userOpt = realmCfg.findUser(username);
    if (userOpt.isEmpty() || !userOpt.get().getPassword().equals(password)) {
      return tokenError(401, "invalid_grant", "Invalid user credentials");
    }

    UserConfig user = userOpt.get();
    String scope = params.getOrDefault("scope", "openid profile email");

    return buildTokenResponse(issuer, username, clientCfg.getClientId(), user.getRoles(), username,
        scope);
  }

  private HttpResponse handleRefreshToken(RealmConfig realmCfg, ClientConfig clientCfg,
      Map<String, String> params, String issuer) {
    String refreshToken = params.getOrDefault("refresh_token", "");
    if (refreshToken.isEmpty()) {
      return tokenError(400, "invalid_request", "Missing refresh_token");
    }

    try {
      com.nimbusds.jwt.SignedJWT parsed = com.nimbusds.jwt.SignedJWT.parse(refreshToken);
      com.nimbusds.jwt.JWTClaimsSet claims = parsed.getJWTClaimsSet();
      String subject = claims.getSubject();
      String preferredUsername = claims.getStringClaim("preferred_username");
      List<String> roles = claims.getStringListClaim("roles");
      if (roles == null) {
        roles = Collections.emptyList();
      }
      String scope = claims.getStringClaim("scope");
      if (scope == null) {
        scope = "openid profile email";
      }
      return buildTokenResponse(issuer, subject, clientCfg.getClientId(), roles,
          preferredUsername, scope);
    } catch (java.text.ParseException e) {
      LOG.debug("Could not parse refresh_token JWT, issuing generic token", e);
      String subject = "service-account-" + clientCfg.getClientId();
      List<String> roles = clientCfg.getServiceAccountRoles() != null
          ? clientCfg.getServiceAccountRoles()
          : realmCfg.getRoles();
      return buildTokenResponse(issuer, subject, clientCfg.getClientId(), roles, null,
          "openid profile email");
    }
  }

  private HttpResponse buildTokenResponse(String issuer, String subject, String clientId,
      List<String> roles, String preferredUsername, String scope) {
    JwtKeyProvider keyProvider = JwtKeyProvider.getInstance();

    String accessToken = buildAccessToken(keyProvider, issuer, subject, clientId, roles,
        preferredUsername, scope);
    String idToken = buildIdToken(keyProvider, issuer, subject, clientId, preferredUsername, scope);
    String refreshJwt = buildRefreshToken(keyProvider, issuer, subject, clientId, roles,
        preferredUsername, scope);

    OpenidTokenResponse response = new OpenidTokenResponse();
    response.setAccessToken(accessToken);
    response.setTokenType("Bearer");
    response.setExpiresIn(EXPIRES_IN_SECONDS);
    response.setRefreshToken(refreshJwt);
    response.setIdToken(idToken);
    response.setScope(scope);

    return HttpResponse.response()
        .withStatusCode(200)
        .withHeader("Cache-Control", "no-store")
        .withHeader("Pragma", "no-cache")
        .withContentType(MediaType.APPLICATION_JSON)
        .withBody(writeAsString(response));
  }

  private String buildAccessToken(JwtKeyProvider kp, String issuer, String subject,
      String clientId, List<String> roles, String preferredUsername, String scope) {
    long now = System.currentTimeMillis();
    Map<String, Serializable> claims = new LinkedHashMap<>();
    claims.put("iss", issuer);
    claims.put("sub", subject);
    claims.put("typ", "Bearer");
    claims.put("azp", clientId);
    claims.put("iat", new Date(now));
    claims.put("exp", new Date(now + (long) EXPIRES_IN_SECONDS * 1000));
    claims.put("jti", UUID.randomUUID().toString());
    claims.put("scope", scope);

    LinkedHashMap<String, Serializable> realmAccess = new LinkedHashMap<>();
    realmAccess.put("roles", (Serializable) roles);
    claims.put("realm_access", realmAccess);

    LinkedHashMap<String, Serializable> clientRoles = new LinkedHashMap<>();
    clientRoles.put("roles", (Serializable) roles);
    LinkedHashMap<String, Serializable> resourceAccess = new LinkedHashMap<>();
    resourceAccess.put(clientId, clientRoles);
    claims.put("resource_access", resourceAccess);

    if (preferredUsername != null) {
      claims.put("preferred_username", preferredUsername);
    }
    return kp.signJwt(claims);
  }

  private String buildIdToken(JwtKeyProvider kp, String issuer, String subject,
      String clientId, String preferredUsername, String scope) {
    long now = System.currentTimeMillis();
    Map<String, Serializable> claims = new LinkedHashMap<>();
    claims.put("iss", issuer);
    claims.put("sub", subject);
    claims.put("typ", "ID");
    claims.put("azp", clientId);
    claims.put("iat", new Date(now));
    claims.put("exp", new Date(now + (long) EXPIRES_IN_SECONDS * 1000));
    claims.put("jti", UUID.randomUUID().toString());
    if (preferredUsername != null) {
      claims.put("preferred_username", preferredUsername);
    }
    if (scope != null) {
      claims.put("scope", scope);
    }
    return kp.signJwt(claims);
  }

  private String buildRefreshToken(JwtKeyProvider kp, String issuer, String subject,
      String clientId, List<String> roles, String preferredUsername, String scope) {
    long now = System.currentTimeMillis();
    long refreshExpiry = 30L * 24 * 60 * 60 * 1000;
    Map<String, Serializable> claims = new LinkedHashMap<>();
    claims.put("iss", issuer);
    claims.put("sub", subject);
    claims.put("typ", "Refresh");
    claims.put("azp", clientId);
    claims.put("iat", new Date(now));
    claims.put("exp", new Date(now + refreshExpiry));
    claims.put("jti", UUID.randomUUID().toString());
    claims.put("roles", (Serializable) roles);
    if (preferredUsername != null) {
      claims.put("preferred_username", preferredUsername);
    }
    if (scope != null) {
      claims.put("scope", scope);
    }
    return kp.signJwt(claims);
  }

  // ---- helpers ----

  static String extractRealm(String path) {
    String prefix = "/realms/";
    int start = path.indexOf(prefix);
    if (start < 0) {
      return "";
    }
    int nameStart = start + prefix.length();
    int nameEnd = path.indexOf('/', nameStart);
    return nameEnd < 0 ? path.substring(nameStart) : path.substring(nameStart, nameEnd);
  }

  private Map<String, String> parseFormBody(HttpRequest request) {
    if (request.getBody() == null || request.getBody().getValue() == null) {
      return Collections.emptyMap();
    }
    String body = request.getBody().getValue().toString();
    if (body.isEmpty()) {
      return Collections.emptyMap();
    }
    return Arrays.stream(body.split("&"))
        .map(s -> s.split("=", 2))
        .collect(Collectors.toMap(
            arr -> urlDecode(arr[0]),
            arr -> arr.length > 1 ? urlDecode(arr[1]) : "",
            (a, b) -> a));
  }

  private static String urlDecode(String s) {
    return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8);
  }

  private String[] extractBasicAuth(HttpRequest request) {
    String authHeader = request.getFirstHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith("Basic ")) {
      return null;
    }
    try {
      String decoded = new String(Base64.getDecoder().decode(authHeader.substring(6)),
          StandardCharsets.UTF_8);
      String[] parts = decoded.split(":", 2);
      return parts.length == 2 ? parts : null;
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private HttpResponse tokenError(int status, String error, String description) {
    TokenError err = new TokenError(error, description, "");
    return HttpResponse.response()
        .withStatusCode(status)
        .withContentType(MediaType.APPLICATION_JSON)
        .withBody(writeAsString(err));
  }

  private HttpResponse errorResponse(int status, String message) {
    return HttpResponse.response()
        .withStatusCode(status)
        .withContentType(MediaType.APPLICATION_JSON)
        .withBody("{\"error\":\"" + message + "\"}");
  }
}
