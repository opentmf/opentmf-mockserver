package org.opentmf.mockserver.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class VerifyBuilderTests {

  @RegisterExtension
  static MockServerSupport mock = MockServerSupport.create();

  private static final HttpClient HTTP = HttpClient.newHttpClient();

  @Test
  void times_matchesExactCount() throws Exception {
    mock.stub().get("/counted").respondStatus(200);

    HTTP.send(HttpRequest.newBuilder(URI.create(mock.baseUrl() + "/counted")).GET().build(),
        HttpResponse.BodyHandlers.discarding());
    HTTP.send(HttpRequest.newBuilder(URI.create(mock.baseUrl() + "/counted")).GET().build(),
        HttpResponse.BodyHandlers.discarding());
    HTTP.send(HttpRequest.newBuilder(URI.create(mock.baseUrl() + "/counted")).GET().build(),
        HttpResponse.BodyHandlers.discarding());

    mock.verify().get("/counted").times(3);
  }

  @Test
  void never_asserts_uncalledPath() {
    mock.stub().get("/uncalled").respondStatus(200);
    mock.verify().get("/uncalled").never();
  }

  @Test
  void times_wrongCount_throwsAssertion() throws Exception {
    mock.stub().get("/actual-1-call").respondStatus(200);
    HTTP.send(HttpRequest.newBuilder(URI.create(mock.baseUrl() + "/actual-1-call")).GET().build(),
        HttpResponse.BodyHandlers.discarding());

    assertThatThrownBy(() -> mock.verify().get("/actual-1-call").times(5))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void withHeader_narrowsMatch() throws Exception {
    mock.stub().post("/hdr").respondStatus(204);

    HTTP.send(
        HttpRequest.newBuilder(URI.create(mock.baseUrl() + "/hdr"))
            .header("X-Trace", "T1")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build(),
        HttpResponse.BodyHandlers.discarding());

    mock.verify().post("/hdr").withHeader("X-Trace", "T1").once();
    mock.verify().post("/hdr").withHeader("X-Trace", "T2").never();
  }

  @Test
  void terminalWithoutStart_throws() {
    VerifyBuilder vb = mock.verify();
    assertThatThrownBy(() -> vb.once()).isInstanceOf(IllegalStateException.class);
  }
}
