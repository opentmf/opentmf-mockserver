package org.mockserver.version;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class Version {

    private static final String VERSION = "2.0.1-SNAPSHOT";
    private static final String ARTIFACT_ID = "opentmf-mockserver";
    private static final String GROUP_ID = "org.opentmf.mockserver";

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
