package org.opentmf.mockserver.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.io.Serializable;

/**
 * Represents an error that occurs during token generation or validation. This class is used for
 * serializing/deserializing error responses in a standardized format. The error fields follow the
 * snake_case naming convention when serialized to JSON.
 *
 * @author Yusuf BOZKURT
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class TokenError implements Serializable {

  private final String error; // The error code.
  private final String errorDescription; // Description of the error.
  private final String errorUri; // URI containing more information about the error.

  /**
   * Constructs a TokenError with the specified error, error description, and error URI.
   *
   * @param error The error code.
   * @param errorDescription Description of the error.
   * @param errorUri URI containing more information about the error.
   */
  public TokenError(String error, String errorDescription, String errorUri) {
    this.error = error;
    this.errorDescription = errorDescription;
    this.errorUri = errorUri;
  }

  /**
   * Retrieves the error code.
   *
   * @return The error code.
   */
  public String getError() {
    return error;
  }

  /**
   * Retrieves the description of the error.
   *
   * @return Description of the error.
   */
  public String getErrorDescription() {
    return errorDescription;
  }

  /**
   * Retrieves the URI containing more information about the error.
   *
   * @return URI containing more information about the error.
   */
  public String getErrorUri() {
    return errorUri;
  }
}
