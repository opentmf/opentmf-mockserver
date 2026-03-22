package org.opentmf.mockserver.callback;

import static org.opentmf.mockserver.util.JacksonUtil.writeAsString;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpClassCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.MediaType;
import org.mockserver.server.initialize.ExpectationInitializer;
import org.opentmf.mockserver.keycloak.KeycloakConfig;
import org.opentmf.mockserver.keycloak.RealmConfig;
import org.opentmf.mockserver.token.JwtKeyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registers OAuth2 / OIDC expectations when MockServer starts.
 *
 * <ul>
 *   <li>{@code GET /.well-known/jwks.json} -- backward-compatible global JWKS</li>
 *   <li>Per-realm Keycloak-compatible endpoints:
 *     <ul>
 *       <li>{@code GET /realms/{realm}/protocol/openid-connect/certs}</li>
 *       <li>{@code GET /realms/{realm}/.well-known/openid-configuration}</li>
 *       <li>{@code POST /realms/{realm}/protocol/openid-connect/token}</li>
 *     </ul>
 *   </li>
 * </ul>
 */
public class JwksExpectationInitializer implements ExpectationInitializer {

  private static final Logger LOG = LoggerFactory.getLogger(JwksExpectationInitializer.class);

  @Override
  public Expectation[] initializeExpectations() {
    String jwksJson = JwtKeyProvider.getInstance().getJwksJson();
    List<Expectation> expectations = new ArrayList<>();

    expectations.add(globalJwks(jwksJson));

    KeycloakConfig config = KeycloakConfig.getInstance();
    for (RealmConfig realm : config.getRealms()) {
      String realmName = realm.getName();
      String baseUrl = config.getBaseUrl();
      String oidcBase = "/realms/" + realmName + "/protocol/openid-connect";

      expectations.add(realmJwks(oidcBase, jwksJson));
      expectations.add(realmDiscovery(realmName, baseUrl));
      expectations.add(realmToken(oidcBase));
    }

    Expectation openapi = openapiSpec();
    if (openapi != null) {
      expectations.add(openapi);
    }

    LOG.info("Registered {} OIDC expectations ({} realm(s))",
        expectations.size(), config.getRealms().size());
    return expectations.toArray(new Expectation[0]);
  }

  private Expectation globalJwks(String jwksJson) {
    Expectation e = Expectation.when(
            HttpRequest.request()
                .withMethod("GET")
                .withPath("/.well-known/jwks.json"),
            Times.unlimited(), null)
        .thenRespond(
            HttpResponse.response()
                .withStatusCode(200)
                .withContentType(MediaType.APPLICATION_JSON)
                .withHeader("Cache-Control", "public, max-age=3600")
                .withBody(jwksJson));
    LOG.info("  GET /.well-known/jwks.json");
    return e;
  }

  private Expectation realmJwks(String oidcBase, String jwksJson) {
    String path = oidcBase + "/certs";
    Expectation e = Expectation.when(
            HttpRequest.request().withMethod("GET").withPath(path),
            Times.unlimited(), null)
        .thenRespond(
            HttpResponse.response()
                .withStatusCode(200)
                .withContentType(MediaType.APPLICATION_JSON)
                .withHeader("Cache-Control", "public, max-age=3600")
                .withBody(jwksJson));
    LOG.info("  GET {}", path);
    return e;
  }

  private Expectation realmDiscovery(String realm, String baseUrl) {
    String issuer = baseUrl + "/realms/" + realm;
    String oidc = issuer + "/protocol/openid-connect";

    Map<String, Object> discovery = new LinkedHashMap<>();
    discovery.put("issuer", issuer);
    discovery.put("authorization_endpoint", oidc + "/auth");
    discovery.put("token_endpoint", oidc + "/token");
    discovery.put("userinfo_endpoint", oidc + "/userinfo");
    discovery.put("end_session_endpoint", oidc + "/logout");
    discovery.put("jwks_uri", oidc + "/certs");
    discovery.put("grant_types_supported",
        new String[]{"client_credentials", "password", "refresh_token", "authorization_code"});
    discovery.put("response_types_supported", new String[]{"code"});
    discovery.put("subject_types_supported", new String[]{"public"});
    discovery.put("id_token_signing_alg_values_supported", new String[]{"RS256"});
    discovery.put("token_endpoint_auth_methods_supported",
        new String[]{"client_secret_post", "client_secret_basic"});
    discovery.put("scopes_supported", new String[]{"openid", "profile", "email"});

    String path = "/realms/" + realm + "/.well-known/openid-configuration";
    Expectation e = Expectation.when(
            HttpRequest.request().withMethod("GET").withPath(path),
            Times.unlimited(), null)
        .thenRespond(
            HttpResponse.response()
                .withStatusCode(200)
                .withContentType(MediaType.APPLICATION_JSON)
                .withHeader("Cache-Control", "public, max-age=3600")
                .withBody(writeAsString(discovery)));
    LOG.info("  GET {}", path);
    return e;
  }

  private Expectation realmToken(String oidcBase) {
    String path = oidcBase + "/token";
    Expectation e = Expectation.when(
            HttpRequest.request().withMethod("POST").withPath(path),
            Times.unlimited(), null)
        .thenRespond(
            HttpClassCallback.callback(KeycloakTokenCallback.class.getName()));
    LOG.info("  POST {}", path);
    return e;
  }

  private static final String OPENAPI_RESOURCE = "opentmf-mockserver-openapi.yaml";
  private static final String OPENAPI_PATH = "/mockserver/openapi";

  private Expectation openapiSpec() {
    try (InputStream is = getClass().getClassLoader().getResourceAsStream(OPENAPI_RESOURCE)) {
      if (is == null) {
        LOG.warn("OpenAPI spec not found on classpath: {}", OPENAPI_RESOURCE);
        return null;
      }
      String yaml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
      Expectation e = Expectation.when(
              HttpRequest.request().withMethod("GET").withPath(OPENAPI_PATH),
              Times.unlimited(), null)
          .thenRespond(
              HttpResponse.response()
                  .withStatusCode(200)
                  .withHeader("Content-Type", "application/yaml")
                  .withHeader("Cache-Control", "public, max-age=3600")
                  .withBody(yaml));
      LOG.info("  GET {}", OPENAPI_PATH);
      return e;
    } catch (IOException e) {
      LOG.warn("Failed to load OpenAPI spec: {}", e.getMessage());
      return null;
    }
  }
}
