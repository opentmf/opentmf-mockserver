package org.mockserver.version;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Version {

  private static final String VERSION;
  private static final String ARTIFACT_ID;
  private static final String GROUP_ID;

  static {
    Properties props = new Properties();
    try (InputStream is = Version.class.getClassLoader()
        .getResourceAsStream("mockserver-version.properties")) {
      if (is != null) {
        props.load(is);
      }
    } catch (IOException ignored) {
    }
    VERSION = props.getProperty("version", "unknown");
    ARTIFACT_ID = props.getProperty("artifact-id", "opentmf-mockserver");
    GROUP_ID = props.getProperty("group-id", "org.opentmf");
  }

  public static String getVersion() {
    return VERSION;
  }

  public static String getArtifactId() {
    return ARTIFACT_ID;
  }

  public static String getGroupId() {
    return GROUP_ID;
  }

  public static String getMajorMinorVersion() {
    String[] parts = VERSION.split("\\.");
    if (parts.length >= 2) {
      return parts[0] + "." + parts[1];
    }
    return VERSION;
  }

  public static boolean matchesMajorMinorVersion(String serverVersion) {
    if (isNotBlank(serverVersion)) {
      String clientMajorMinor = getMajorMinorVersion();
      String serverMajorMinor;
      String[] parts = serverVersion.split("\\.");
      if (parts.length >= 2) {
        serverMajorMinor = parts[0] + "." + parts[1];
      } else {
        serverMajorMinor = serverVersion;
      }
      return clientMajorMinor.equals(serverMajorMinor);
    }
    return true;
  }
}
