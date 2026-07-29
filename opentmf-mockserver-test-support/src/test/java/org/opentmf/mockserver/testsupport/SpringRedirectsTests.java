package org.opentmf.mockserver.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.context.DynamicPropertyRegistry;

class SpringRedirectsTests {

  @RegisterExtension
  static MockServerSupport mock = MockServerSupport.create();

  @Test
  void redirectApiClients_populatesBaseUrlAndClearsContextPath() {
    RecordingRegistry registry = new RecordingRegistry();
    mock.redirectApiClients(registry, "onedms", "asgw");

    assertThat(registry.snapshot())
        .containsOnlyKeys(
            "opentmf.api-clients.onedms.base-url",
            "opentmf.api-clients.onedms.context-path",
            "opentmf.api-clients.asgw.base-url",
            "opentmf.api-clients.asgw.context-path")
        .containsEntry("opentmf.api-clients.onedms.base-url", mock.baseUrl())
        .containsEntry("opentmf.api-clients.onedms.context-path", "")
        .containsEntry("opentmf.api-clients.asgw.base-url", mock.baseUrl());
  }

  @Test
  void redirectHttpClients_populatesBaseUrl() {
    RecordingRegistry registry = new RecordingRegistry();
    mock.redirectHttpClients(registry, "raw");

    assertThat(registry.snapshot())
        .containsOnlyKeys("opentmf.http-clients.raw.base-url")
        .containsEntry("opentmf.http-clients.raw.base-url", mock.baseUrl());
  }

  @Test
  void redirectJwks_populatesJwkSetUri_andRegistersJwksEndpoint() {
    RecordingRegistry registry = new RecordingRegistry();
    mock.redirectJwks(registry);

    Object uri = registry.snapshot().get("opentmf.security.jwk-set-uri");
    assertThat(uri).isNotNull().hasToString(
        mock.baseUrl() + "/realms/" + OidcMockSupport.DEFAULT_REALM
            + "/protocol/openid-connect/certs");
  }

  @Test
  void redirectJwks_forCustomRealm_usesGivenRealm() {
    RecordingRegistry registry = new RecordingRegistry();
    mock.redirectJwks(registry, "custom");

    assertThat(registry.snapshot().get("opentmf.security.jwk-set-uri").toString())
        .endsWith("/realms/custom/protocol/openid-connect/certs");
  }

  private static class RecordingRegistry implements DynamicPropertyRegistry {
    final Map<String, Supplier<Object>> suppliers = new LinkedHashMap<>();

    @Override
    public void add(String name, Supplier<Object> valueSupplier) {
      suppliers.put(name, valueSupplier);
    }

    Map<String, Object> snapshot() {
      Map<String, Object> out = new LinkedHashMap<>();
      suppliers.forEach((k, v) -> out.put(k, v.get()));
      return out;
    }
  }
}
