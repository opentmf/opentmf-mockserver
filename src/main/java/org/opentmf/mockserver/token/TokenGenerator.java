package org.opentmf.mockserver.token;

import org.opentmf.mockserver.exception.TokenException;
import org.opentmf.mockserver.model.TokenError;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

/**
 * Abstract class for generating tokens. This class provides methods for token generation and
 * request validation. Subclasses must implement the generateTokenResponse() method.
 *
 * @author Yusuf BOZKURT
 */
public abstract class TokenGenerator {

  static final String GRANT_TYPE_CLIENT_CREDENTIALS = "client_credentials";
  static final String GRANT_TYPE_PASSWORD = "password";
  static final String GRANT_TYPE_REFRESH_TOKEN = "refresh_token";
  static final String PARAM_USERNAME = "username";
  static final String PARAM_PASSWORD = "password";
  static final String PARAM_REFRESH_TOKEN = "refresh_token";

  final List<String> validGrantTypes =
      Arrays.asList(GRANT_TYPE_CLIENT_CREDENTIALS, GRANT_TYPE_PASSWORD, GRANT_TYPE_REFRESH_TOKEN);
  final HttpRequest httpRequest;

  /**
   * Constructs a TokenGenerator with the provided HTTP request.
   *
   * @param httpRequest The HTTP request from which to generate the token.
   */
  TokenGenerator(HttpRequest httpRequest) {
    this.httpRequest = httpRequest;
  }

  /**
   * Generates a token response. Subclasses must implement this method to generate the appropriate
   * token response.
   *
   * @return HttpResponse representing the token response.
   */
  public abstract HttpResponse generateTokenResponse();

  /**
   * Validates the token request. This method checks if the grant type is valid and if the required
   * parameters are present.
   *
   * @throws TokenException if the token request is invalid.
   */
  final void validateRequest() {
    String grantType = extractParameter("grant_type");

    if (validGrantTypes.contains(grantType)) {
      if (GRANT_TYPE_PASSWORD.equals(grantType)) {
        String username = extractParameter(PARAM_USERNAME);
        String password = extractParameter(PARAM_PASSWORD);
        if (StringUtils.isEmpty(username) || StringUtils.isEmpty(password)) {
          throw new TokenException(getInvalidRequestParameterError(), HttpStatus.SC_BAD_REQUEST);
        }
      } else if (GRANT_TYPE_REFRESH_TOKEN.equals(grantType)) {
        String refreshToken = extractParameter(PARAM_REFRESH_TOKEN);
        if (StringUtils.isEmpty(refreshToken)) {
          throw new TokenException(getInvalidRequestParameterError(), HttpStatus.SC_BAD_REQUEST);
        }
      }
    } else {
      throw new TokenException(
          new TokenError(
              "Unsupported grant_type",
              "The authorization grant type is not supported by the authorization server",
              ""),
          HttpStatus.SC_BAD_REQUEST);
    }
  }

  /**
   * Generates an error object for invalid request parameters.
   *
   * @return TokenError representing the error due to invalid request parameters.
   */
  private TokenError getInvalidRequestParameterError() {
    return new TokenError(
        "invalid_request",
        "The request is missing a required\n"
            + "parameter, includes an unsupported\n"
            + "parameter value (other than grant type),\n"
            + "repeats a parameter, includes multiple\n"
            + "credentials, utilizes more than one\n"
            + "mechanism for authenticating the client, or\n"
            + "is otherwise malformed",
        "");
  }

  /**
   * Retrieves the value of a parameter from the request body.
   *
   * @param parameter The name of the parameter to extract.
   * @return The value of the parameter, or an empty string if not found.
   */
  String extractParameter(String parameter) {
    String bodyString =
        this.httpRequest.getBody().getValue().toString(); // Request body's string representation

    Map<String, String> paramMap =
        Arrays.stream(bodyString.split("&"))
            .map(s -> s.split("="))
            .collect(Collectors.toMap(arr -> arr[0], arr -> arr.length > 1 ? arr[1] : ""));

    return paramMap.getOrDefault(parameter, "");
  }
}
