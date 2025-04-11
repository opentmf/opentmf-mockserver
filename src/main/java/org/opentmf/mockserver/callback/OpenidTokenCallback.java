package org.opentmf.mockserver.callback;

import org.opentmf.mockserver.token.OpenidTokenGenerator;
import org.opentmf.mockserver.token.TokenGenerator;
import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

/**
 * Callback implementation for handling Sh token requests in the MockServer. This callback is
 * responsible for generating token responses for Sh token requests. It delegates the token
 * generation process to the ShTokenGenerator class.
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
