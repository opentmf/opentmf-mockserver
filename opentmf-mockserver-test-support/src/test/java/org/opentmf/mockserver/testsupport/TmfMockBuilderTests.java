package org.opentmf.mockserver.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class TmfMockBuilderTests {

  @RegisterExtension
  static MockServerSupport mock = MockServerSupport.create();

  private static final HttpClient HTTP = HttpClient.newHttpClient();

  @Test
  void post_registersDynamicPostCallback_realHttpPostReturns201WithBodyEcho() throws Exception {
    mock.tmf("test").post("/document");

    HttpResponse<String> resp = HTTP.send(
        HttpRequest.newBuilder(URI.create(mock.baseUrl() + "/document"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"title\":\"a\"}"))
            .build(),
        HttpResponse.BodyHandlers.ofString());

    assertThat(resp.statusCode()).isEqualTo(201);
    assertThat(resp.body()).contains("\"title\":\"a\"");
    assertThat(resp.body()).contains("\"id\":");
    assertThat(resp.body()).contains("\"href\":");
  }

  @Test
  void crud_postThenGetById_returnsPostedResource() throws Exception {
    mock.tmf("test").crud("/thing");

    HttpResponse<String> post = HTTP.send(
        HttpRequest.newBuilder(URI.create(mock.baseUrl() + "/thing"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"widget\"}"))
            .build(),
        HttpResponse.BodyHandlers.ofString());
    assertThat(post.statusCode()).isEqualTo(201);
    String id = post.body().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

    HttpResponse<String> get = HTTP.send(
        HttpRequest.newBuilder(URI.create(mock.baseUrl() + "/thing/" + id)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
    assertThat(get.statusCode()).isEqualTo(200);
    assertThat(get.body()).contains("\"name\":\"widget\"").contains("\"id\":\"" + id + "\"");
  }

  @Test
  void crud_deleteById_thenGetReturns404() throws Exception {
    mock.tmf("test").crud("/gone");

    HttpResponse<String> post = HTTP.send(
        HttpRequest.newBuilder(URI.create(mock.baseUrl() + "/gone"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"x\":1}"))
            .build(),
        HttpResponse.BodyHandlers.ofString());
    String id = post.body().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

    HttpResponse<String> del = HTTP.send(
        HttpRequest.newBuilder(URI.create(mock.baseUrl() + "/gone/" + id)).DELETE().build(),
        HttpResponse.BodyHandlers.ofString());
    assertThat(del.statusCode()).isEqualTo(204);

    HttpResponse<String> get = HTTP.send(
        HttpRequest.newBuilder(URI.create(mock.baseUrl() + "/gone/" + id)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
    assertThat(get.statusCode()).isEqualTo(404);
  }

  @Test
  void apiClientId_labelPropagatesToBuilder() {
    TmfMockBuilder builder = mock.tmf("labeled");
    assertThat(builder.apiClientId()).isEqualTo("labeled");
  }
}
