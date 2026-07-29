package org.opentmf.mockserver.testsupport;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.mockserver.client.MockServerClient;
import org.mockserver.matchers.Times;
import org.mockserver.model.Delay;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.MediaType;

/**
 * Fluent static-expectation registration for non-TMF endpoints (KBA lookups, external
 * gateway stubs, etc.). Terminating {@code respond*} methods register the expectation
 * against the {@link MockServerClient}; there is no return value to chain further.
 *
 * <pre>{@code
 * mock.stub().get("/kba/{key}").respondJson(200, "{\"value\":42}");
 * mock.stub().post("/sms").header("X-Api-Key", "s3cret").respondStatus(204);
 * mock.stub().get("/flaky").respondSequence(500, 200);
 * }</pre>
 */
public class StubBuilder {

  private final MockServerClient client;
  private HttpRequest current;

  StubBuilder(MockServerClient client) {
    this.client = client;
  }

  /** Start defining a {@code GET <path>} expectation. */
  public StubBuilder get(String path) {
    return method("GET", path);
  }

  /** Start defining a {@code POST <path>} expectation. */
  public StubBuilder post(String path) {
    return method("POST", path);
  }

  /** Start defining a {@code PUT <path>} expectation. */
  public StubBuilder put(String path) {
    return method("PUT", path);
  }

  /** Start defining a {@code DELETE <path>} expectation. */
  public StubBuilder delete(String path) {
    return method("DELETE", path);
  }

  /** Start defining an expectation with an arbitrary method + path. */
  public StubBuilder method(String method, String path) {
    this.current = request().withMethod(method).withPath(path);
    return this;
  }

  /** Add a path parameter matcher (e.g. {@code /kba/{key}} matching {@code key=abc}). */
  public StubBuilder pathParam(String name, String value) {
    ensureStarted();
    current.withPathParameter(name, value);
    return this;
  }

  /** Add a query parameter matcher. */
  public StubBuilder queryParam(String name, String value) {
    ensureStarted();
    current.withQueryStringParameter(name, value);
    return this;
  }

  /** Add a header matcher. */
  public StubBuilder header(String name, String value) {
    ensureStarted();
    current.withHeader(name, value);
    return this;
  }

  /** Match against a specific JSON body. */
  public StubBuilder jsonBody(String body) {
    ensureStarted();
    current.withBody(body).withContentType(MediaType.APPLICATION_JSON);
    return this;
  }

  /** Respond with the given status and JSON body. Terminates the builder. */
  public void respondJson(int status, String body) {
    respond(response().withStatusCode(status)
        .withContentType(MediaType.APPLICATION_JSON)
        .withBody(body));
  }

  /** Respond with the given status and no body. Terminates the builder. */
  public void respondStatus(int status) {
    respond(response().withStatusCode(status));
  }

  /**
   * Respond after a delay — useful for timeout / retry testing. Terminates the builder.
   */
  public void respondDelayed(int status, String body, Duration delay) {
    respond(
        response()
            .withStatusCode(status)
            .withContentType(MediaType.APPLICATION_JSON)
            .withBody(body)
            .withDelay(new Delay(TimeUnit.MILLISECONDS, delay.toMillis())));
  }

  /**
   * Respond with a sequence of status codes: the Nth call gets the Nth entry. Useful for
   * retry-path testing (e.g. {@code respondSequence(500, 200)} — first call fails, second
   * succeeds). Each entry gets an empty body; use {@link #respondSequence(HttpResponse...)}
   * for full control.
   */
  public void respondSequence(int... statuses) {
    HttpResponse[] responses = new HttpResponse[statuses.length];
    for (int i = 0; i < statuses.length; i++) {
      responses[i] = response().withStatusCode(statuses[i]);
    }
    respondSequence(responses);
  }

  /**
   * Respond with a sequence of full responses: the Nth call gets the Nth entry. Registers
   * one expectation per response, each with {@code Times.exactly(1)}, in order.
   */
  public void respondSequence(HttpResponse... responses) {
    ensureStarted();
    List<HttpRequest> requests = new ArrayList<>(responses.length);
    for (HttpResponse ignored : responses) {
      requests.add(current);
    }
    for (int i = 0; i < responses.length; i++) {
      client.when(requests.get(i), Times.exactly(1)).respond(responses[i]);
    }
    current = null;
  }

  private void respond(HttpResponse response) {
    ensureStarted();
    client.when(current).respond(response);
    current = null;
  }

  private void ensureStarted() {
    if (current == null) {
      throw new IllegalStateException(
          "Call get/post/put/delete/method(...) first before adding matchers or responding.");
    }
  }
}
