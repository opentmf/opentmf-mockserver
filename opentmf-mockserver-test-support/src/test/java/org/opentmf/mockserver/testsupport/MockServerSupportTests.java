package org.opentmf.mockserver.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class MockServerSupportTests {

  @RegisterExtension
  static MockServerSupport mock = MockServerSupport.create();

  @Test
  void create_startsOnRandomFreePort_reachableViaBaseUrl() throws Exception {
    assertThat(mock.port()).isPositive();
    assertThat(mock.baseUrl()).isEqualTo("http://localhost:" + mock.port());
    assertThat(mock.client()).isNotNull();

    mock.stub().get("/ping").respondStatus(204);
    HttpResponse<String> resp = HttpClient.newHttpClient().send(
        HttpRequest.newBuilder(URI.create(mock.baseUrl() + "/ping")).GET().build(),
        HttpResponse.BodyHandlers.ofString());
    assertThat(resp.statusCode()).isEqualTo(204);
  }

  @Test
  void start_isIdempotent_reusesSameServer() {
    int firstPort = mock.port();
    mock.start();
    assertThat(mock.port()).isEqualTo(firstPort);
  }

  @Test
  void tmf_stub_verify_oidc_returnNonNullBuilders() {
    assertThat(mock.tmf("onedms")).isNotNull();
    assertThat(mock.stub()).isNotNull();
    assertThat(mock.verify()).isNotNull();
    assertThat(mock.oidc()).isNotNull();
  }
}
