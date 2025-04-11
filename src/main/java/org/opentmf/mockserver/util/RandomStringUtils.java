package org.opentmf.mockserver.util;

import java.security.SecureRandom;

public class RandomStringUtils {
  private RandomStringUtils() {}

  private static final SecureRandom random = new SecureRandom(); // Compliant
  private static final String CHARACTERS =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

  public static String randomString(int length) {
    StringBuilder builder = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      int randomIndex = random.nextInt(CHARACTERS.length());
      char randomChar = CHARACTERS.charAt(randomIndex);
      builder.append(randomChar);
    }
    return builder.toString();
  }
}
