package org.opentmf.mockserver.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class OidcMockSupportTests {

  @RegisterExtension
  static MockServerSupport mock = MockServerSupport.create();

  @Test
  void token_containsExpectedClaims() throws Exception {
    String jwt = mock.oidc().realm("dev").clientId("my-app")
        .tokenFor("alice", "reader", "writer");

    JWTClaimsSet claims = SignedJWT.parse(jwt).getJWTClaimsSet();
    assertThat(claims.getIssuer()).isEqualTo(mock.baseUrl() + "/realms/dev");
    assertThat(claims.getStringClaim("azp")).isEqualTo("my-app");
    assertThat(claims.getStringClaim("preferred_username")).isEqualTo("alice");

    @SuppressWarnings("unchecked")
    Map<String, Object> realmAccess = (Map<String, Object>) claims.getClaim("realm_access");
    @SuppressWarnings("unchecked")
    List<String> roles = (List<String>) realmAccess.get("roles");
    assertThat(roles).containsExactly("reader", "writer");
  }

  @Test
  void token_defaultsFillIn_whenNotOverridden() throws Exception {
    String jwt = mock.token("admin");
    JWTClaimsSet claims = SignedJWT.parse(jwt).getJWTClaimsSet();
    assertThat(claims.getIssuer()).endsWith("/realms/" + OidcMockSupport.DEFAULT_REALM);
    assertThat(claims.getStringClaim("azp")).isEqualTo(OidcMockSupport.DEFAULT_CLIENT_ID);
    assertThat(claims.getStringClaim("preferred_username")).isEqualTo("test-user");
  }

  @Test
  void registerJwksEndpoint_servesJwksJson_thatVerifiesTokenSignature() throws Exception {
    OidcMockSupport oidc = mock.oidc();
    oidc.registerJwksEndpoint();

    HttpResponse<String> jwksResp = HttpClient.newHttpClient().send(
        HttpRequest.newBuilder(URI.create(mock.baseUrl() + oidc.jwksPath())).GET().build(),
        HttpResponse.BodyHandlers.ofString());
    assertThat(jwksResp.statusCode()).isEqualTo(200);

    JWKSet jwks = JWKSet.parse(jwksResp.body());
    assertThat(jwks.getKeys()).hasSize(1);
    RSAKey key = (RSAKey) jwks.getKeys().get(0);
    assertThat(key.getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);

    String jwt = mock.token("reader");
    ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
    processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256,
        new ImmutableJWKSet<>(jwks)));
    JWTClaimsSet verified = processor.process(jwt, null);
    assertThat(verified.getStringClaim("preferred_username")).isEqualTo("test-user");
  }

  @Test
  void bearerHeader_returnsBearerPrefixedToken() {
    String header = mock.bearerHeader("admin");
    assertThat(header).startsWith("Bearer ");
    assertThat(header.substring("Bearer ".length())).isNotBlank();
  }
}
