package com.pia.mockserver.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Abstract base class representing a token. This class defines common properties for different
 * types of tokens and follows the snake_case naming convention when serialized to JSON.
 *
 * @author Yusuf BOZKURT
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public abstract class TokenBase {

  private String accessToken; // The access token.
  private String tokenType; // The token type.
  private String scope; // The scope of the token.

  /**
   * Retrieves the access token.
   *
   * @return The access token.
   */
  public String getAccessToken() {
    return accessToken;
  }

  /**
   * Sets the access token.
   *
   * @param accessToken The access token.
   */
  public void setAccessToken(String accessToken) {
    this.accessToken = accessToken;
  }

  /**
   * Retrieves the token type.
   *
   * @return The token type.
   */
  public String getTokenType() {
    return tokenType;
  }

  /**
   * Sets the token type.
   *
   * @param tokenType The token type.
   */
  public void setTokenType(String tokenType) {
    this.tokenType = tokenType;
  }

  /**
   * Retrieves the scope of the token.
   *
   * @return The scope of the token.
   */
  public String getScope() {
    return scope;
  }

  /**
   * Sets the scope of the token.
   *
   * @param scope The scope of the token.
   */
  public void setScope(String scope) {
    this.scope = scope;
  }
}
