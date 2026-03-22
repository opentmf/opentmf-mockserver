package org.mockserver.logging;

import static org.mockserver.character.Character.NEW_LINE;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import java.util.Base64;
import org.apache.commons.codec.binary.Hex;

public class BinaryArrayFormatter {

  public static String byteArrayToString(byte[] bytes) {
    if (bytes != null && bytes.length > 0) {
      return "base64:"
          + NEW_LINE
          + "  "
          + Joiner.on("\n  ")
              .join(Splitter.fixedLength(64).split(Base64.getEncoder().encodeToString(bytes)))
          + NEW_LINE
          + "hex:"
          + NEW_LINE
          + "  "
          + Joiner.on("\n  ")
              .join(Splitter.fixedLength(64).split(String.valueOf(Hex.encodeHex(bytes))));
    } else {
      return "base64:" + NEW_LINE + NEW_LINE + "hex:" + NEW_LINE;
    }
  }
}
