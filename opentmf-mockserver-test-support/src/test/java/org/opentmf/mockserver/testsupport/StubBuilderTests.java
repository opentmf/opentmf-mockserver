package org.opentmf.mockserver.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class StubBuilderTests {

  @RegisterExtension
  static MockServerSupport mock = MockServerSupport.create();

  private static final HttpClient HTTP = HttpClient.newHttpClient();

  @Test
  void get_respondJson_returnsStatusAndBody() throws Exception {
    mock.stub().get("/kba/abc").respondJson(200, "{\"value\":\"foo\"}");

    HttpResponse<String> resp = HTTP.send(
        HttpRequest.newBuilder(URI.create(mock.baseUrl() + "/kba/abc")).GET().build(),
        HttpResponse.BodyHandlers.ofString());

    assertThat(resp.statusCode()).isEqualTo(200);
    assertThat(resp.body()).isEqualTo("{\"value\":\"foo\"}");
    assertThat(resp.headers().firstValue("content-type")).hasValueSatisfying(
        ct -> assertThat(ct).contains("application/json"));
  }

  @Test
  void post_headerMatcher_onlyRespondsWhenHeaderPresent() throws Exception {
    mock.stub().post("/sms").header("X-Api-Key", "s3cret").respondStatus(204);

    HttpResponse<String> withHeader = HTTP.send(
        HttpRequest.newBuilder(URI.create(mock.baseUrl() + "/sms"))
            .header("X-Api-Key", "s3cret")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build(),
        HttpResponse.BodyHandlers.ofString());
    assertThat(withHeader.statusCode()).isEqualTo(204);

    HttpResponse<String> without = HTTP.send(
        HttpRequest.newBuilder(URI.create(mock.baseUrl() + "/sms"))
            .POST(HttpRequest.BodyPublishers.noBody())
            .build(),
        HttpResponse.BodyHandlers.ofString());
    // MockServer returns 404 when no expectation matches
    assertThat(without.statusCode()).isEqualTo(404);
  }

  @Test
  void respondDelayed_delaysBeyondThreshold() throws Exception {
    mock.stub().get("/slow").respondDelayed(200, "{}", Duration.ofMillis(300));

    long start = System.currentTimeMillis();
    HttpResponse<String> resp = HTTP.send(
        HttpRequest.newBuilder(URI.create(mock.baseUrl() + "/slow")).GET().build(),
        HttpResponse.BodyHandlers.ofString());
    long elapsed = System.currentTimeMillis() - start;

    assertThat(resp.statusCode()).isEqualTo(200);
    assertThat(elapsed).isGreaterThanOrEqualTo(250);
  }

  @Test
  void respondSequence_firstCallFails_secondCallSucceeds() throws Exception {
    mock.stub().get("/flaky").respondSequence(500, 200);

    HttpResponse<String> first = HTTP.send(
        HttpRequest.newBuilder(URI.create(mock.baseUrl() + "/flaky")).GET().build(),
        HttpResponse.BodyHandlers.ofString());
    HttpResponse<String> second = HTTP.send(
        HttpRequest.newBuilder(URI.create(mock.baseUrl() + "/flaky")).GET().build(),
        HttpResponse.BodyHandlers.ofString());

    assertThat(first.statusCode()).isEqualTo(500);
    assertThat(second.statusCode()).isEqualTo(200);
  }

  @Test
  void terminalWithoutStart_throws() {
    StubBuilder sb = mock.stub();
    assertThatThrownBy(() -> sb.respondStatus(200))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void put_delete_verbs_registerExpectations() throws Exception {
    mock.stub().put("/x").respondStatus(204);
    mock.stub().delete("/x").respondStatus(202);

    HttpResponse<String> put = HTTP.send(
        HttpRequest.newBuilder(URI.create(mock.baseUrl() + "/x"))
            .PUT(HttpRequest.BodyPublishers.noBody()).build(),
        HttpResponse.BodyHandlers.ofString());
    assertThat(put.statusCode()).isEqualTo(204);

    HttpResponse<String> del = HTTP.send(
        HttpRequest.newBuilder(URI.create(mock.baseUrl() + "/x")).DELETE().build(),
        HttpResponse.BodyHandlers.ofString());
    assertThat(del.statusCode()).isEqualTo(202);
  }

  @Test
  void method_arbitraryVerb_registers() throws Exception {
    mock.stub().method("OPTIONS", "/opt").respondStatus(200);
    HttpResponse<String> resp = HTTP.send(
        HttpRequest.newBuilder(URI.create(mock.baseUrl() + "/opt"))
            .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(),
        HttpResponse.BodyHandlers.ofString());
    assertThat(resp.statusCode()).isEqualTo(200);
  }

  @Test
  void queryParam_narrowsMatch() throws Exception {
    mock.stub().get("/q").queryParam("k", "v").respondStatus(200);

    HttpResponse<String> with = HTTP.send(
        HttpRequest.newBuilder(URI.create(mock.baseUrl() + "/q?k=v")).GET().build(),
        HttpResponse.BodyHandlers.ofString());
    assertThat(with.statusCode()).isEqualTo(200);

    HttpResponse<String> without = HTTP.send(
        HttpRequest.newBuilder(URI.create(mock.baseUrl() + "/q?k=other")).GET().build(),
        HttpResponse.BodyHandlers.ofString());
    assertThat(without.statusCode()).isEqualTo(404);
  }

  @Test
  void pathParam_narrowsMatch() throws Exception {
    mock.stub().get("/pp/{k}").pathParam("k", "abc").respondStatus(200);

    HttpResponse<String> match = HTTP.send(
        HttpRequest.newBuilder(URI.create(mock.baseUrl() + "/pp/abc")).GET().build(),
        HttpResponse.BodyHandlers.ofString());
    assertThat(match.statusCode()).isEqualTo(200);

    HttpResponse<String> other = HTTP.send(
        HttpRequest.newBuilder(URI.create(mock.baseUrl() + "/pp/other")).GET().build(),
        HttpResponse.BodyHandlers.ofString());
    assertThat(other.statusCode()).isEqualTo(404);
  }

  @Test
  void jsonBody_narrowsMatch() throws Exception {
    mock.stub().post("/jb").jsonBody("{\"x\":1}").respondStatus(200);

    HttpResponse<String> match = HTTP.send(
        HttpRequest.newBuilder(URI.create(mock.baseUrl() + "/jb"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"x\":1}"))
            .build(),
        HttpResponse.BodyHandlers.ofString());
    assertThat(match.statusCode()).isEqualTo(200);
  }

  @Test
  void matcherWithoutStart_throws() {
    StubBuilder sb = mock.stub();
    assertThatThrownBy(() -> sb.header("H", "v"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void respondSequence_httpResponseArray_registersEachOnce() throws Exception {
    mock.stub().get("/seq-obj")
        .respondSequence(
            org.mockserver.model.HttpResponse.response().withStatusCode(500),
            org.mockserver.model.HttpResponse.response().withStatusCode(503),
            org.mockserver.model.HttpResponse.response().withStatusCode(200));

    int s1 = HTTP.send(HttpRequest.newBuilder(URI.create(mock.baseUrl() + "/seq-obj")).GET().build(),
        HttpResponse.BodyHandlers.discarding()).statusCode();
    int s2 = HTTP.send(HttpRequest.newBuilder(URI.create(mock.baseUrl() + "/seq-obj")).GET().build(),
        HttpResponse.BodyHandlers.discarding()).statusCode();
    int s3 = HTTP.send(HttpRequest.newBuilder(URI.create(mock.baseUrl() + "/seq-obj")).GET().build(),
        HttpResponse.BodyHandlers.discarding()).statusCode();
    assertThat(new int[]{s1, s2, s3}).containsExactly(500, 503, 200);
  }
}
