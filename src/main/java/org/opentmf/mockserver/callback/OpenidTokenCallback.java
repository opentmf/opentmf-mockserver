package org.opentmf.mockserver.callback;

import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.opentmf.mockserver.token.OpenidTokenGenerator;
import org.opentmf.mockserver.token.TokenGenerator;

/**
 *
 *
 * <h2>OpenidTokenCallback</h2>
 *
 * <ul>
 *   <li>Checks if the payload contains the necessary fields depending on the mandatory attribute
 *       "grant_type" and returns 400 Bad Request if a required parameter is missing from the
 *       request body.
 *   <li>Prepares and returns an OpenID token payload with httpStatus = 200.
 * </ul>
 *
 * @author Yusuf BOZKURT
 */
public class OpenidTokenCallback implements ExpectationResponseCallback {

  /**
   * Handles the incoming HTTP request and generates a token response.
   *
   * @param httpRequest The incoming HTTP request.
   * @return The HTTP response containing the generated token.
   */
  @Override
  public HttpResponse handle(HttpRequest httpRequest) {
    TokenGenerator tokenGenerator = new OpenidTokenGenerator(httpRequest);
    return tokenGenerator.generateTokenResponse();
  }
}
