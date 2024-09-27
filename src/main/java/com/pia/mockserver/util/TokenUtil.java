package com.pia.mockserver.util;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * @author Yusuf BOZKURT
 */
public class TokenUtil {
  private TokenUtil() {}

  public static String generateRandomToken(int length) {
    SecureRandom secureRandom = new SecureRandom();
    byte[] tokenBytes = new byte[length];
    secureRandom.nextBytes(tokenBytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
  }
}
