package org.opentmf.mockserver.testsupport;

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

    VerifyBuilder verifier = mock.verify().get("/actual-1-call");
    assertThatThrownBy(() -> verifier.times(5))
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
    assertThatThrownBy(vb::once).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void put_delete_method_verbsRegisterAndAssertCounts() throws Exception {
    mock.stub().put("/vput").respondStatus(200);
    mock.stub().delete("/vdel").respondStatus(200);
    mock.stub().method("PATCH", "/vpatch").respondStatus(200);

    HTTP.send(HttpRequest.newBuilder(URI.create(mock.baseUrl() + "/vput"))
        .PUT(HttpRequest.BodyPublishers.noBody()).build(),
        HttpResponse.BodyHandlers.discarding());
    HTTP.send(HttpRequest.newBuilder(URI.create(mock.baseUrl() + "/vdel")).DELETE().build(),
        HttpResponse.BodyHandlers.discarding());
    HTTP.send(HttpRequest.newBuilder(URI.create(mock.baseUrl() + "/vpatch"))
        .method("PATCH", HttpRequest.BodyPublishers.noBody()).build(),
        HttpResponse.BodyHandlers.discarding());

    mock.verify().put("/vput").once();
    mock.verify().delete("/vdel").once();
    mock.verify().method("PATCH", "/vpatch").once();
  }

  @Test
  void atLeast_atMost_boundedCounts() throws Exception {
    mock.stub().get("/bounds").respondStatus(200);
    for (int i = 0; i < 3; i++) {
      HTTP.send(HttpRequest.newBuilder(URI.create(mock.baseUrl() + "/bounds")).GET().build(),
          HttpResponse.BodyHandlers.discarding());
    }
    mock.verify().get("/bounds").atLeast(2);
    mock.verify().get("/bounds").atMost(5);
  }

  @Test
  void withJsonBody_narrowsMatch() throws Exception {
    mock.stub().post("/vjb").respondStatus(200);
    HTTP.send(
        HttpRequest.newBuilder(URI.create(mock.baseUrl() + "/vjb"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"a\":1,\"b\":2}"))
            .build(),
        HttpResponse.BodyHandlers.discarding());
    mock.verify().post("/vjb").withJsonBody("{\"a\":1}").once();
    mock.verify().post("/vjb").withJsonBody("{\"a\":9}").never();
  }

  @Test
  void withQueryParam_narrowsMatch() throws Exception {
    mock.stub().get("/vq").respondStatus(200);
    HTTP.send(HttpRequest.newBuilder(URI.create(mock.baseUrl() + "/vq?k=v")).GET().build(),
        HttpResponse.BodyHandlers.discarding());
    mock.verify().get("/vq").withQueryParam("k", "v").once();
    mock.verify().get("/vq").withQueryParam("k", "other").never();
  }

  @Test
  void matcherWithoutStart_throws() {
    VerifyBuilder vb = mock.verify();
    assertThatThrownBy(() -> vb.withHeader("h", "v"))
        .isInstanceOf(IllegalStateException.class);
  }
}
