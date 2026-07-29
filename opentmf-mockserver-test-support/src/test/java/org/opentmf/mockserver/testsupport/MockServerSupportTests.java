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

  @Test
  void keepExpectationsBetweenTests_returnsSameInstance() {
    // Chainable setter — verify it does not blow up and returns self so callers can chain.
    assertThat(mock.keepExpectationsBetweenTests()).isSameAs(mock);
  }

  @Test
  void expect_registersRawExpectation() throws Exception {
    mock.expect(
        org.mockserver.model.HttpRequest.request().withMethod("GET").withPath("/raw"),
        org.mockserver.model.HttpResponse.response().withStatusCode(200).withBody("raw-body"));
    HttpResponse<String> resp = HttpClient.newHttpClient().send(
        HttpRequest.newBuilder(URI.create(mock.baseUrl() + "/raw")).GET().build(),
        HttpResponse.BodyHandlers.ofString());
    assertThat(resp.statusCode()).isEqualTo(200);
    assertThat(resp.body()).isEqualTo("raw-body");
  }

  @Test
  void token_bearerHeader_tokenFor_returnRealJwts() {
    String tok = mock.token("reader");
    String bearer = mock.bearerHeader("reader", "writer");
    String userTok = mock.tokenFor("alice", "admin");
    assertThat(tok.chars().filter(c -> c == '.').count()).isEqualTo(2);
    assertThat(bearer).startsWith("Bearer ");
    assertThat(userTok.chars().filter(c -> c == '.').count()).isEqualTo(2);
  }

  @Test
  void nonJunit_start_stop_cycleWorks() {
    // Uses the public API without JUnit lifecycle — the Cucumber / plain-Java entry-point.
    MockServerSupport aux = MockServerSupport.create();
    try {
      assertThat(aux.port()).isPositive();
      // start() is idempotent while running
      aux.start();
    } finally {
      aux.stop();
    }
    // stop() is idempotent when already stopped
    aux.stop();
  }

  @Test
  void shared_returnsSameInstanceAcrossCalls() {
    MockServerSupport a = MockServerSupport.shared();
    MockServerSupport b = MockServerSupport.shared();
    assertThat(a).isSameAs(b);
    assertThat(a.port()).isPositive();
  }

  @Test
  void shared_afterAll_isNoop_serverStaysUp() throws Exception {
    // Registering the shared instance as a per-class extension must not stop the server
    // when this class's `afterAll` fires — otherwise the next test class using
    // shared() would get a dead server. Simulate the boundary by calling afterAll
    // directly on the shared instance.
    MockServerSupport shared = MockServerSupport.shared();
    int port = shared.port();
    shared.afterAll(null); // would normally stop() — must no-op for shared instances
    assertThat(shared.client()).isNotNull(); // still up
    assertThat(shared.port()).isEqualTo(port);
    // Still reachable end-to-end
    shared.stub().get("/still-alive").respondStatus(204);
    HttpResponse<String> resp = HttpClient.newHttpClient().send(
        HttpRequest.newBuilder(URI.create(shared.baseUrl() + "/still-alive")).GET().build(),
        HttpResponse.BodyHandlers.ofString());
    assertThat(resp.statusCode()).isEqualTo(204);
  }
}
