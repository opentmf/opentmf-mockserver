package com.pia.mockserver.exception;

import com.pia.mockserver.model.TokenError;

/**
 * @author Yusuf BOZKURT
 */
public class TokenException extends RuntimeException {

  private final int httpStatus;
  private final TokenError tokenError;

  public TokenException(TokenError error, int status) {
    super(error.getErrorDescription());
    this.tokenError = error;
    this.httpStatus = status;
  }

  public int getHttpStatus() {
    return httpStatus;
  }

  public TokenError getTokenError() {
    return tokenError;
  }
}
