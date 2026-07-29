package org.opentmf.mockserver.callback;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockserver.model.HttpRequest.request;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.opentmf.mockserver.keycloak.ClientConfig;
import org.opentmf.mockserver.keycloak.RealmConfig;
import org.opentmf.mockserver.util.JacksonUtil;
import tools.jackson.databind.JsonNode;

class KeycloakTokenCallbackTests {

  private static final String TOKEN_PATH = "/realms/realm1/protocol/openid-connect/token";

  private final KeycloakTokenCallback callback = new KeycloakTokenCallback();

  // ---- client_credentials grant ----

  @Test
  void clientCredentials_validClient1_returns200WithRoles() throws ParseException {
    HttpResponse resp =
        callback.handle(
            tokenRequest(
                "grant_type=client_credentials&client_id=client1&client_secret=client1Secret"));

    assertEquals(200, resp.getStatusCode());
    SignedJWT jwt = parseAccessToken(resp);
    JWTClaimsSet claims = jwt.getJWTClaimsSet();

    assertEquals("service-account-client1", claims.getSubject());
    assertTrue(claims.getIssuer().endsWith("/realms/realm1"));
    assertEquals("client1", claims.getStringClaim("azp"));

    Map<String, Object> realmAccess = claims.getJSONObjectClaim("realm_access");
    assertNotNull(realmAccess);
    @SuppressWarnings("unchecked")
    List<String> roles = (List<String>) realmAccess.get("roles");
    assertTrue(roles.contains("admin"));
    assertTrue(roles.contains("writer"));
    assertTrue(roles.contains("reader"));
  }

  @Test
  void clientCredentials_wrongSecret_returns401() {
    HttpResponse resp =
        callback.handle(
            tokenRequest("grant_type=client_credentials&client_id=client1&client_secret=WRONG"));

    assertEquals(401, resp.getStatusCode());
    JsonNode body = JacksonUtil.readAsTree(resp.getBodyAsString());
    assertEquals("unauthorized_client", body.get("error").asText());
  }

  @Test
  void clientCredentials_client2NotAllowed_returnsError() {
    HttpResponse resp =
        callback.handle(
            tokenRequest(
                "grant_type=client_credentials&client_id=client2&client_secret=client2Secret"));

    assertEquals(400, resp.getStatusCode());
    JsonNode body = JacksonUtil.readAsTree(resp.getBodyAsString());
    assertEquals("unauthorized_client", body.get("error").asText());
  }

  @Test
  void clientCredentials_unknownClient_returns401() {
    HttpResponse resp =
        callback.handle(
            tokenRequest("grant_type=client_credentials&client_id=unknown&client_secret=s"));

    assertEquals(401, resp.getStatusCode());
    JsonNode body = JacksonUtil.readAsTree(resp.getBodyAsString());
    assertEquals("invalid_client", body.get("error").asText());
  }

  @Test
  void clientCredentials_viaBasicAuth_returns200() throws ParseException {
    String creds =
        Base64.getEncoder()
            .encodeToString("client1:client1Secret".getBytes(StandardCharsets.UTF_8));
    HttpRequest req =
        request()
            .withMethod("POST")
            .withPath(TOKEN_PATH)
            .withBody("grant_type=client_credentials")
            .withHeader("Authorization", "Basic " + creds);

    HttpResponse resp = callback.handle(req);
    assertEquals(200, resp.getStatusCode());

    SignedJWT jwt = parseAccessToken(resp);
    assertEquals("service-account-client1", jwt.getJWTClaimsSet().getSubject());
  }

  // ---- password grant ----

  @Test
  void password_validUserViaClient2_returns200WithUserRoles() throws ParseException {
    HttpResponse resp =
        callback.handle(
            tokenRequest(
                "grant_type=password&client_id=client2&client_secret=client2Secret"
                    + "&username=admin_usr&password=admin_pwd"));

    assertEquals(200, resp.getStatusCode());
    SignedJWT jwt = parseAccessToken(resp);
    JWTClaimsSet claims = jwt.getJWTClaimsSet();

    assertEquals("admin_usr", claims.getSubject());
    assertEquals("admin_usr", claims.getStringClaim("preferred_username"));
    assertEquals("client2", claims.getStringClaim("azp"));

    @SuppressWarnings("unchecked")
    List<String> roles = (List<String>) claims.getJSONObjectClaim("realm_access").get("roles");
    assertEquals(3, roles.size());
  }

  @Test
  void password_writerUser_hasOnlyWriterAndReaderRoles() throws ParseException {
    HttpResponse resp =
        callback.handle(
            tokenRequest(
                "grant_type=password&client_id=client2&client_secret=client2Secret"
                    + "&username=writer_usr&password=writer_pwd"));

    assertEquals(200, resp.getStatusCode());
    SignedJWT jwt = parseAccessToken(resp);

    @SuppressWarnings("unchecked")
    List<String> roles =
        (List<String>) jwt.getJWTClaimsSet().getJSONObjectClaim("realm_access").get("roles");
    assertEquals(2, roles.size());
    assertTrue(roles.contains("writer"));
    assertTrue(roles.contains("reader"));
    assertFalse(roles.contains("admin"));
  }

  @Test
  void password_readerUser_hasOnlyReaderRole() throws ParseException {
    HttpResponse resp =
        callback.handle(
            tokenRequest(
                "grant_type=password&client_id=client2&client_secret=client2Secret"
                    + "&username=reader_usr&password=reader_pwd"));

    assertEquals(200, resp.getStatusCode());
    SignedJWT jwt = parseAccessToken(resp);

    @SuppressWarnings("unchecked")
    List<String> roles =
        (List<String>) jwt.getJWTClaimsSet().getJSONObjectClaim("realm_access").get("roles");
    assertEquals(1, roles.size());
    assertTrue(roles.contains("reader"));
  }

  @Test
  void password_wrongPassword_returns401() {
    HttpResponse resp =
        callback.handle(
            tokenRequest(
                "grant_type=password&client_id=client2&client_secret=client2Secret"
                    + "&username=admin_usr&password=WRONG"));

    assertEquals(401, resp.getStatusCode());
    JsonNode body = JacksonUtil.readAsTree(resp.getBodyAsString());
    assertEquals("invalid_grant", body.get("error").asText());
  }

  @Test
  void password_unknownUser_returns401() {
    HttpResponse resp =
        callback.handle(
            tokenRequest(
                "grant_type=password&client_id=client2&client_secret=client2Secret"
                    + "&username=nobody&password=x"));

    assertEquals(401, resp.getStatusCode());
  }

  @Test
  void password_viaPublicUiClient_noSecretRequired() throws ParseException {
    HttpResponse resp =
        callback.handle(
            tokenRequest(
                "grant_type=password&client_id=uiClient"
                    + "&username=reader_usr&password=reader_pwd"));

    assertEquals(200, resp.getStatusCode());
    SignedJWT jwt = parseAccessToken(resp);
    assertEquals("reader_usr", jwt.getJWTClaimsSet().getSubject());
    assertEquals("uiClient", jwt.getJWTClaimsSet().getStringClaim("azp"));
  }

  @Test
  void password_client1NotAllowed_returnsError() {
    HttpResponse resp =
        callback.handle(
            tokenRequest(
                "grant_type=password&client_id=client1&client_secret=client1Secret"
                    + "&username=admin_usr&password=admin_pwd"));

    assertEquals(400, resp.getStatusCode());
    JsonNode body = JacksonUtil.readAsTree(resp.getBodyAsString());
    assertEquals("unauthorized_client", body.get("error").asText());
  }

  // ---- refresh_token grant ----

  @Test
  void refreshToken_viaUiClient_returnsNewTokens() throws ParseException {
    // First obtain an access + refresh token via password grant
    HttpResponse initial =
        callback.handle(
            tokenRequest(
                "grant_type=password&client_id=uiClient"
                    + "&username=writer_usr&password=writer_pwd"));
    assertEquals(200, initial.getStatusCode());

    JsonNode initialBody = JacksonUtil.readAsTree(initial.getBodyAsString());
    String refreshToken = initialBody.get("refresh_token").asText();
    assertNotNull(refreshToken);

    // Use the refresh token
    HttpResponse refreshResp =
        callback.handle(
            tokenRequest(
                "grant_type=refresh_token&client_id=uiClient&refresh_token=" + refreshToken));

    assertEquals(200, refreshResp.getStatusCode());
    SignedJWT jwt = parseAccessToken(refreshResp);
    JWTClaimsSet claims = jwt.getJWTClaimsSet();

    assertEquals("writer_usr", claims.getSubject());
    assertEquals("writer_usr", claims.getStringClaim("preferred_username"));

    @SuppressWarnings("unchecked")
    List<String> roles = (List<String>) claims.getJSONObjectClaim("realm_access").get("roles");
    assertTrue(roles.contains("writer"));
    assertTrue(roles.contains("reader"));
  }

  @Test
  void refreshToken_client1NotAllowed_returnsError() {
    HttpResponse resp =
        callback.handle(
            tokenRequest(
                "grant_type=refresh_token&client_id=client1&client_secret=client1Secret"
                    + "&refresh_token=some-token"));

    assertEquals(400, resp.getStatusCode());
  }

  // ---- realm and path validation ----

  @Test
  void unknownRealm_returns404() {
    HttpRequest req =
        request()
            .withMethod("POST")
            .withPath("/realms/unknownRealm/protocol/openid-connect/token")
            .withBody(
                "grant_type=client_credentials&client_id=client1&client_secret=client1Secret");

    HttpResponse resp = callback.handle(req);
    assertEquals(404, resp.getStatusCode());
  }

  @Test
  void missingClientId_returns400() {
    HttpResponse resp = callback.handle(tokenRequest("grant_type=client_credentials"));
    assertEquals(400, resp.getStatusCode());
  }

  // ---- JWT structure validation ----

  @Test
  void accessTokenHasKeycloakClaims() throws ParseException {
    HttpResponse resp =
        callback.handle(
            tokenRequest(
                "grant_type=client_credentials&client_id=client1&client_secret=client1Secret"));

    SignedJWT jwt = parseAccessToken(resp);
    JWTClaimsSet claims = jwt.getJWTClaimsSet();

    assertNotNull(claims.getIssuer());
    assertNotNull(claims.getSubject());
    assertNotNull(claims.getIssueTime());
    assertNotNull(claims.getExpirationTime());
    assertNotNull(claims.getJWTID());
    assertEquals("Bearer", claims.getStringClaim("typ"));
    assertNotNull(claims.getJSONObjectClaim("realm_access"));
    assertNotNull(claims.getJSONObjectClaim("resource_access"));
  }

  @Test
  void responseContainsAllTokenFields() {
    HttpResponse resp =
        callback.handle(
            tokenRequest(
                "grant_type=client_credentials&client_id=client1&client_secret=client1Secret"));

    JsonNode body = JacksonUtil.readAsTree(resp.getBodyAsString());
    assertNotNull(body.get("access_token"));
    assertNotNull(body.get("refresh_token"));
    assertNotNull(body.get("id_token"));
    assertNotNull(body.get("token_type"));
    assertNotNull(body.get("expires_in"));
    assertNotNull(body.get("scope"));
    assertEquals("Bearer", body.get("token_type").asText());
  }

  @Test
  void defaultExpiresIn_is3600() {
    HttpResponse resp =
        callback.handle(
            tokenRequest(
                "grant_type=client_credentials&client_id=client1&client_secret=client1Secret"));

    JsonNode body = JacksonUtil.readAsTree(resp.getBodyAsString());
    assertEquals(3600, body.get("expires_in").asInt());
  }

  @Test
  void resolveExpiresIn_clientOverridesRealm() {
    RealmConfig realm = new RealmConfig();
    realm.setExpiresIn(1800);

    ClientConfig client = new ClientConfig();
    client.setExpiresIn(300);

    assertEquals(300, KeycloakTokenCallback.resolveExpiresIn(realm, client));
  }

  @Test
  void resolveExpiresIn_fallsBackToRealm() {
    RealmConfig realm = new RealmConfig();
    realm.setExpiresIn(1800);

    ClientConfig client = new ClientConfig();

    assertEquals(1800, KeycloakTokenCallback.resolveExpiresIn(realm, client));
  }

  @Test
  void resolveExpiresIn_fallsBackToDefault() {
    RealmConfig realm = new RealmConfig();
    ClientConfig client = new ClientConfig();

    assertEquals(3600, KeycloakTokenCallback.resolveExpiresIn(realm, client));
  }

  @Test
  void extractRealm_parsesCorrectly() {
    assertEquals(
        "realm1",
        KeycloakTokenCallback.extractRealm("/realms/realm1/protocol/openid-connect/token"));
    assertEquals(
        "myRealm",
        KeycloakTokenCallback.extractRealm("/realms/myRealm/protocol/openid-connect/token"));
    assertEquals("", KeycloakTokenCallback.extractRealm("/some/other/path"));
  }

  // ---- helpers ----

  private HttpRequest tokenRequest(String body) {
    return request().withMethod("POST").withPath(TOKEN_PATH).withBody(body);
  }

  private SignedJWT parseAccessToken(HttpResponse resp) throws ParseException {
    JsonNode body = JacksonUtil.readAsTree(resp.getBodyAsString());
    return SignedJWT.parse(body.get("access_token").asText());
  }
}
