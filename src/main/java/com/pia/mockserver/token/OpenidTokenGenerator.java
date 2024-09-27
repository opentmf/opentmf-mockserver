package com.pia.mockserver.token;

import static com.pia.mockserver.util.ErrorResponseUtil.getErrorResponse;
import static com.pia.mockserver.util.JacksonUtil.writeAsString;
import static com.pia.mockserver.util.TokenUtil.generateRandomToken;

import com.pia.mockserver.exception.TokenException;
import com.pia.mockserver.model.OpenidTokenResponse;
import com.pia.mockserver.model.TokenBase;
import java.util.UUID;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.model.MediaType;

/**
 * Token generator implementation for SH service. This class generates tokens specific to the SH
 * service. Extends TokenGenerator abstract class.
 *
 * @author Yusuf BOZKURT
 */
public class OpenidTokenGenerator extends TokenGenerator {

  /**
   * Constructs an OpenidTokenGenerator with the provided HTTP request.
   *
   * @param httpRequest The HTTP request from which to generate the token.
   */
  public OpenidTokenGenerator(HttpRequest httpRequest) {
    super(httpRequest);
  }

  /**
   * Generates a token response for the SH service.
   *
   * @return HttpResponse representing the token response.
   */
  @Override
  public HttpResponse generateTokenResponse() {
    try {
      validateRequest();
    } catch (TokenException exception) {
      return getErrorResponse(exception.getHttpStatus(), exception.getTokenError());
    }

    return HttpResponse.response()
        .withStatusCode(HttpStatusCode.OK_200.code())
        .withHeader("Cache-Control", "no-store")
        .withHeader("Pragma", "no-cache")
        .withContentType(MediaType.APPLICATION_JSON)
        .withBody(writeAsString(generateToken()));
  }

  /**
   * Generates a token specific to the SH service.
   *
   * @return TokenBase representing the generated token.
   */
  public TokenBase generateToken() {
    OpenidTokenResponse tokenResponse = new OpenidTokenResponse();
    tokenResponse.setAccessToken(generateJwtToken());
    tokenResponse.setTokenType("Bearer");
    tokenResponse.setExpiresIn(3599);
    tokenResponse.setRefreshToken(UUID.randomUUID().toString());
    tokenResponse.setIdToken(generateJwtToken());
    tokenResponse.setScope(extractParameter("scope"));
    return tokenResponse;
  }

  /**
   * Generates a JWT token.
   *
   * @return The generated JWT token.
   */
  private String generateJwtToken() {
    String header = generateRandomToken(32);
    String payload = generateRandomToken(64);
    String signature = generateRandomToken(32);
    return header + "." + payload + "." + signature;
  }
}
