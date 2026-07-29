package org.opentmf.mockserver.token;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import java.io.Serializable;
import java.security.interfaces.RSAPublicKey;
import java.util.Map;
import org.mockserver.authentication.jwt.JWTGenerator;
import org.mockserver.keys.AsymmetricKeyGenerator;
import org.mockserver.keys.AsymmetricKeyPair;
import org.mockserver.keys.AsymmetricKeyPairAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton that holds the RSA key pair used for signing JWTs and exposing the public key as JWKS.
 * Both the token callback and the JWKS initializer share this instance so that tokens can be
 * verified against the published key.
 */
@SuppressWarnings("java:S6548") // singleton is deliberate — RSA keypair shared by token signer and JWKS publisher
public final class JwtKeyProvider {

  private static final Logger LOG = LoggerFactory.getLogger(JwtKeyProvider.class);

  @SuppressWarnings({"java:S3077", "java:S6548"}) // DCL singleton — intentional pattern.
  private static volatile JwtKeyProvider instance;

  private final JWTGenerator jwtGenerator;
  private final String jwksJson;

  private JwtKeyProvider() {
    AsymmetricKeyPair keyPair =
        AsymmetricKeyGenerator.createAsymmetricKeyPair(AsymmetricKeyPairAlgorithm.RSA2048_SHA256);
    this.jwtGenerator = new JWTGenerator(keyPair);

    RSAPublicKey publicKey = (RSAPublicKey) keyPair.getKeyPair().getPublic();
    RSAKey jwk =
        new RSAKey.Builder(publicKey)
            .keyID(keyPair.getKeyId())
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256)
            .build();
    this.jwksJson = new JWKSet(jwk).toString();
    LOG.info("JWT key pair initialised with kid=\"{}\"", keyPair.getKeyId());
  }

  public static JwtKeyProvider getInstance() {
    if (instance == null) {
      synchronized (JwtKeyProvider.class) {
        if (instance == null) {
          instance = new JwtKeyProvider();
        }
      }
    }
    return instance;
  }

  /** Sign a JWT with the given claims. */
  public String signJwt(Map<String, Serializable> claims) {
    return jwtGenerator.signJWT(claims);
  }

  /** JWKS JSON containing only the public key, ready to serve to clients. */
  public String getJwksJson() {
    return jwksJson;
  }
}
