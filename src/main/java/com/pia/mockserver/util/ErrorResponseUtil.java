package com.pia.mockserver.util;

import static com.pia.mockserver.util.JacksonUtil.writeAsString;

import com.pia.mockserver.model.Error;
import com.pia.mockserver.model.TokenError;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.model.MediaType;

/**
 * Utility class for generating error responses for HTTP requests. This class provides methods to
 * construct HttpResponse objects representing error responses.
 *
 * @author Yusuf BOZKURT
 */
public class ErrorResponseUtil {

  private ErrorResponseUtil() {}

  /**
   * Constructs an error response HttpResponse object with the given status code and message.
   *
   * @param statusCode The HTTP status code of the error response.
   * @param message The error message to be included in the response body.
   * @return HttpResponse object representing the error response.
   */
  public static HttpResponse getErrorResponse(HttpStatusCode statusCode, String message) {
    Error error = new Error(message, statusCode.code(), statusCode.name());
    return HttpResponse.response()
        .withStatusCode(statusCode.code())
        .withContentType(MediaType.APPLICATION_JSON)
        .withBody(writeAsString(error));
  }

  /**
   * Constructs an error response HttpResponse object with the given status code and Error object.
   *
   * @param statusCode The HTTP status code of the error response.
   * @param error The Error object containing details of the error.
   * @return HttpResponse object representing the error response.
   */
  public static HttpResponse getErrorResponse(HttpStatusCode statusCode, Error error) {
    return HttpResponse.response()
        .withStatusCode(statusCode.code())
        .withContentType(MediaType.APPLICATION_JSON)
        .withBody(writeAsString(error));
  }

  /**
   * Constructs an error response HttpResponse object with the given status code and TokenError
   * object.
   *
   * @param statusCode The HTTP status code of the error response.
   * @param tokenError The TokenError object containing details of the error related to tokens.
   * @return HttpResponse object representing the error response.
   */
  public static HttpResponse getErrorResponse(int statusCode, TokenError tokenError) {
    return HttpResponse.response()
        .withStatusCode(statusCode)
        .withContentType(MediaType.APPLICATION_JSON)
        .withBody(writeAsString(tokenError));
  }
}
