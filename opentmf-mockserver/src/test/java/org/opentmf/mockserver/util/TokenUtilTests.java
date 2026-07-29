package org.opentmf.mockserver.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class TokenUtilTests {

  private static final Pattern BASE64_URL_NO_PADDING = Pattern.compile("^[A-Za-z0-9_-]*$");

  @Test
  void generateRandomToken_producesUrlSafeBase64WithoutPadding() {
    String token = TokenUtil.generateRandomToken(32);
    assertNotNull(token);
    assertFalse(token.isEmpty());
    assertFalse(token.contains("="), "URL encoder without padding must not emit '='");
    assertTrue(
        BASE64_URL_NO_PADDING.matcher(token).matches(),
        "Token must use URL-safe base64 alphabet only");
  }

  @Test
  void generateRandomToken_differentLengths_produceDifferentSizedOutputs() {
    String shortToken = TokenUtil.generateRandomToken(8);
    String longToken = TokenUtil.generateRandomToken(64);
    assertTrue(longToken.length() > shortToken.length());
  }

  @Test
  void generateRandomToken_zeroLength_returnsEmptyString() {
    assertEquals("", TokenUtil.generateRandomToken(0));
  }

  @Test
  void generateRandomToken_repeatedCallsReturnDifferentValues() {
    String a = TokenUtil.generateRandomToken(16);
    String b = TokenUtil.generateRandomToken(16);
    assertNotEquals(a, b);
  }

  @Test
  void privateConstructor_isInvocableViaReflectionForCoverage() throws Exception {
    Constructor<TokenUtil> ctor = TokenUtil.class.getDeclaredConstructor();
    assertTrue(Modifier.isPrivate(ctor.getModifiers()));
    ctor.setAccessible(true);
    assertInstanceOf(TokenUtil.class, ctor.newInstance());
  }
}
