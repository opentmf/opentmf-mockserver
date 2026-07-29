package org.opentmf.mockserver.model;

import org.mockserver.model.HttpStatusCode;

/**
 * @author Yusuf BOZKURT
 */
public class Error {
  private final int code;
  private final String message;
  private final String reason;
  private final String status;

  public Error(String message, int code, String status) {
    this(message, null, code, status);
  }

  public Error(String message, String reason, int code, String status) {
    this.code = code;
    this.message = message;
    this.reason = reason;
    this.status = status;
  }

  public int getCode() {
    return code;
  }

  public String getMessage() {
    return message;
  }

  public String getStatus() {
    return status;
  }

  public String getReason() {
    return reason;
  }

  public static Error createErrorContextForNotFound() {
    return new Error(
        "Repeat the request with new or updated Request-URI",
        "The server has not found anything matching the Request-URI",
        HttpStatusCode.NOT_FOUND_404.code(),
        HttpStatusCode.NOT_FOUND_404.reasonPhrase());
  }
}
