package org.mockserver.streams;

import static org.mockserver.character.Character.NEW_LINE;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.mockserver.logging.MockServerLogger;

/**
 * @author jamesdbloom
 */
public class IOStreamUtils {
  private final MockServerLogger mockServerLogger;

  public IOStreamUtils(MockServerLogger mockServerLogger) {
    this.mockServerLogger = mockServerLogger;
  }

  public static String readHttpInputStreamToString(Socket socket) {
    try {
      BufferedReader bufferedReader =
          new BufferedReader(new InputStreamReader(socket.getInputStream()));
      StringBuilder result = new StringBuilder();
      String line;
      Integer contentLength = null;
      while ((line = bufferedReader.readLine()) != null) {
        if (line.startsWith("content-length") || line.startsWith("Content-Length")) {
          contentLength = Integer.parseInt(line.split(":")[1].trim());
        }
        if (line.length() == 0) {
          if (contentLength != null) {
            result.append(NEW_LINE);
            for (int position = 0; position < contentLength; position++) {
              result.append((char) bufferedReader.read());
            }
          }
          break;
        }
        result.append(line).append(NEW_LINE);
      }
      return result.toString();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static String readSocketToString(Socket socket) {
    StringBuilder result = new StringBuilder();
    try {
      InputStream inputStream = socket.getInputStream();
      do {
        final byte[] buffer = new byte[10000];
        final int readBytes = inputStream.read(buffer);
        result.append(new String(Arrays.copyOfRange(buffer, 0, readBytes), StandardCharsets.UTF_8));
      } while (inputStream.available() > 0);
      return result.toString();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
