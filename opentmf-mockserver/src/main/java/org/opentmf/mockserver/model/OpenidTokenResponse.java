package org.opentmf.mockserver.model;

/**
 * Represents a response containing a token specific to the Sh token type. Extends the TokenBase
 * class to inherit common token properties. This class is used to deserialize Sh token responses.
 *
 * @author Yusuf BOZKURT
 */
public class OpenidTokenResponse extends TokenBase {

  private String refreshToken; // The refresh token.
  private String idToken; // The ID token.
  private int expiresIn; // The expiration time of the access token in seconds.

  /**
   * Retrieves the ID token.
   *
   * @return The ID token.
   */
  public String getIdToken() {
    return idToken;
  }

  /**
   * Sets the ID token.
   *
   * @param idToken The ID token.
   */
  public void setIdToken(String idToken) {
    this.idToken = idToken;
  }

  /**
   * Retrieves the expiration time of the access token in seconds.
   *
   * @return The expiration time of the access token.
   */
  public int getExpiresIn() {
    return expiresIn;
  }

  /**
   * Sets the expiration time of the access token in seconds.
   *
   * @param expiresIn The expiration time of the access token.
   */
  public void setExpiresIn(int expiresIn) {
    this.expiresIn = expiresIn;
  }

  /**
   * Retrieves the refresh token.
   *
   * @return The refresh token.
   */
  public String getRefreshToken() {
    return refreshToken;
  }

  /**
   * Sets the refresh token.
   *
   * @param refreshToken The refresh token.
   */
  public void setRefreshToken(String refreshToken) {
    this.refreshToken = refreshToken;
  }
}
