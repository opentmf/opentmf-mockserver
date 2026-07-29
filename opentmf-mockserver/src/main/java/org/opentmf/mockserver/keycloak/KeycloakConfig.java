package org.opentmf.mockserver.keycloak;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Root configuration model for the Keycloak mock. Loaded once at startup from either a mounted JSON
 * file or the built-in classpath default.
 *
 * <p>Resolution order:
 *
 * <ol>
 *   <li>System property {@code keycloak.config.path} (set via env var {@code KEYCLOAK_CONFIG})
 *   <li>File at {@code /config/keycloak-mock.json}
 *   <li>Classpath resource {@code default-keycloak-config.json}
 * </ol>
 */
@SuppressWarnings("java:S6548") // singleton is deliberate — Keycloak mock state must be process-global
public class KeycloakConfig {

  private static final Logger LOG = LoggerFactory.getLogger(KeycloakConfig.class);
  private static final String DEFAULT_FILE_PATH = "/config/keycloak-mock.json";
  private static final String CLASSPATH_RESOURCE = "default-keycloak-config.json";

  @SuppressWarnings({"java:S3077", "java:S6548"}) // DCL singleton — intentional pattern.
  private static volatile KeycloakConfig instance;

  private String baseUrl = "http://localhost:1080";
  private List<RealmConfig> realms = Collections.emptyList();

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public List<RealmConfig> getRealms() {
    return realms;
  }

  public void setRealms(List<RealmConfig> realms) {
    this.realms = realms;
  }

  public Optional<RealmConfig> findRealm(String name) {
    return realms.stream().filter(r -> r.getName().equals(name)).findFirst();
  }

  /** Lazy-loaded singleton. Thread-safe via double-checked locking. */
  public static KeycloakConfig getInstance() {
    if (instance == null) {
      synchronized (KeycloakConfig.class) {
        if (instance == null) {
          instance = load();
        }
      }
    }
    return instance;
  }

  static KeycloakConfig load() {
    ObjectMapper mapper =
        JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    String configPath =
        System.getProperty(
            "keycloak.config.path",
            System.getenv().getOrDefault("KEYCLOAK_CONFIG", DEFAULT_FILE_PATH));

    File file = new File(configPath);
    if (file.isFile()) {
      try {
        KeycloakConfig cfg = mapper.readValue(file, KeycloakConfig.class);
        LOG.info("Loaded Keycloak mock config from file: {}", file.getAbsolutePath());
        return cfg;
      } catch (Exception e) {
        throw new IllegalStateException(
            "Failed to parse Keycloak config from " + file.getAbsolutePath(), e);
      }
    }

    try (InputStream is =
        KeycloakConfig.class.getClassLoader().getResourceAsStream(CLASSPATH_RESOURCE)) {
      if (is != null) {
        KeycloakConfig cfg = mapper.readValue(is, KeycloakConfig.class);
        LOG.info("Loaded default Keycloak mock config from classpath: {}", CLASSPATH_RESOURCE);
        return cfg;
      }
    } catch (IOException e) {
      LOG.error("Failed to parse default Keycloak config from classpath: {}", e.getMessage());
    }

    LOG.warn("No Keycloak config found; using empty config (no realms)");
    return new KeycloakConfig();
  }
}
