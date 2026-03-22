package org.opentmf.mockserver.callback;

import static org.junit.jupiter.api.Assertions.*;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import org.junit.jupiter.api.Test;
import org.mockserver.mock.Expectation;
import org.opentmf.mockserver.keycloak.KeycloakConfig;
import org.opentmf.mockserver.token.JwtKeyProvider;
import org.opentmf.mockserver.util.JacksonUtil;
import tools.jackson.databind.JsonNode;

class JwksExpectationInitializerTests {

  @Test
  void initializerRegistersGlobalAndPerRealmExpectations() throws ParseException {
    JwksExpectationInitializer initializer = new JwksExpectationInitializer();
    Expectation[] expectations = initializer.initializeExpectations();

    int realmCount = KeycloakConfig.getInstance().getRealms().size();
    // 1 global JWKS + 3 per realm (certs, discovery, token) + 1 OpenAPI spec
    assertEquals(1 + realmCount * 3 + 1, expectations.length);

    // First expectation is the global JWKS
    Expectation globalJwks = expectations[0];
    String responseBody = globalJwks.getHttpResponse().getBodyAsString();
    assertNotNull(responseBody);

    JWKSet jwkSet = JWKSet.parse(responseBody);
    assertFalse(jwkSet.getKeys().isEmpty());
    assertInstanceOf(RSAKey.class, jwkSet.getKeys().get(0));

    RSAKey rsaKey = (RSAKey) jwkSet.getKeys().get(0);
    assertFalse(rsaKey.isPrivate(), "JWKS must only contain the public key");
    assertNotNull(rsaKey.getKeyID());
  }

  @Test
  void realmCertsEndpointContainsValidJwks() throws ParseException {
    Expectation[] expectations =
        new JwksExpectationInitializer().initializeExpectations();

    // Second expectation is the realm JWKS (certs)
    Expectation realmCerts = expectations[1];
    String body = realmCerts.getHttpResponse().getBodyAsString();
    JWKSet jwkSet = JWKSet.parse(body);
    assertFalse(jwkSet.getKeys().isEmpty());
    assertInstanceOf(RSAKey.class, jwkSet.getKeys().get(0));
  }

  @Test
  void realmDiscoveryContainsRequiredFields() {
    Expectation[] expectations =
        new JwksExpectationInitializer().initializeExpectations();

    // Third expectation is the discovery document
    Expectation discovery = expectations[2];
    String body = discovery.getHttpResponse().getBodyAsString();
    JsonNode json = JacksonUtil.readAsTree(body);

    assertNotNull(json.get("issuer"));
    assertNotNull(json.get("token_endpoint"));
    assertNotNull(json.get("jwks_uri"));
    assertNotNull(json.get("grant_types_supported"));
    assertNotNull(json.get("token_endpoint_auth_methods_supported"));

    assertTrue(json.get("issuer").asText().contains("/realms/realm1"));
    assertTrue(json.get("token_endpoint").asText().contains("/token"));
    assertTrue(json.get("jwks_uri").asText().contains("/certs"));
  }

  @Test
  void jwksKeyCanVerifySignedToken() throws Exception {
    String jwksJson = JwtKeyProvider.getInstance().getJwksJson();
    JWKSet jwkSet = JWKSet.parse(jwksJson);
    RSAKey rsaKey = (RSAKey) jwkSet.getKeys().get(0);

    java.util.Map<String, java.io.Serializable> claims = new java.util.LinkedHashMap<>();
    claims.put("sub", "test-user");
    String token = JwtKeyProvider.getInstance().signJwt(claims);

    SignedJWT jwt = SignedJWT.parse(token);
    com.nimbusds.jose.crypto.RSASSAVerifier verifier =
        new com.nimbusds.jose.crypto.RSASSAVerifier(rsaKey);
    assertTrue(jwt.verify(verifier), "JWT signature must be valid against the published JWKS key");
    assertEquals("test-user", jwt.getJWTClaimsSet().getSubject());
  }
}
