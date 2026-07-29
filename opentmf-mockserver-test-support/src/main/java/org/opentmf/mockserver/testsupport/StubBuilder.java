package org.opentmf.mockserver.testsupport;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.mockserver.client.MockServerClient;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.Delay;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.MediaType;

/**
 * Fluent static-expectation registration for non-TMF endpoints (KBA lookups, external
 * gateway stubs, etc.). Terminating {@code respond*} methods register the expectation
 * against the {@link MockServerClient} and return a {@link Registration} handle so the
 * caller can later {@link Registration#clear()} just that expectation without wiping the
 * whole server.
 *
 * <pre>{@code
 * Registration r1 = mock.stub().get("/kba/{key}").respondJson(200, "{\"value\":42}");
 * mock.stub().post("/sms").header("X-Api-Key", "s3cret").respondStatus(204);
 * mock.stub().get("/flaky").limit(3).respondStatus(500);        // matches at most 3 times
 * mock.stub().get("/seq").respondSequence(500, 200);            // 1st→500, 2nd→200
 * r1.clear();                                                    // remove just /kba/{key}
 * }</pre>
 */
public class StubBuilder {

  private final MockServerClient client;
  private HttpRequest current;
  private Times times = Times.unlimited();

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
    this.times = Times.unlimited();
    return this;
  }

  /**
   * Cap the number of times this expectation matches (equivalent to
   * {@code Times.exactly(n)}). After the {@code n}th match, the expectation is retired
   * and subsequent calls fall through to whatever other expectation matches — or 404 if
   * none does. Applies to the next {@code respond*} call only; each new
   * {@code get/post/...} resets the cap to unlimited.
   *
   * <p>Ignored by {@link #respondSequence(int...)} / {@link #respondSequence(HttpResponse...)},
   * which impose their own {@code Times.exactly(1)} per element.
   */
  public StubBuilder limit(int n) {
    ensureStarted();
    this.times = Times.exactly(n);
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
  public Registration respondJson(int status, String body) {
    return respond(response().withStatusCode(status)
        .withContentType(MediaType.APPLICATION_JSON)
        .withBody(body));
  }

  /** Respond with the given status and no body. Terminates the builder. */
  public Registration respondStatus(int status) {
    return respond(response().withStatusCode(status));
  }

  /**
   * Respond after a delay — useful for timeout / retry testing. Terminates the builder.
   */
  public Registration respondDelayed(int status, String body, Duration delay) {
    return respond(
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
  public Registration respondSequence(int... statuses) {
    HttpResponse[] responses = new HttpResponse[statuses.length];
    for (int i = 0; i < statuses.length; i++) {
      responses[i] = response().withStatusCode(statuses[i]);
    }
    return respondSequence(responses);
  }

  /**
   * Respond with a sequence of full responses: the Nth call gets the Nth entry. Registers
   * one expectation per response, each with {@code Times.exactly(1)}, in order. All ids
   * are surfaced on the returned {@link Registration}.
   */
  public Registration respondSequence(HttpResponse... responses) {
    ensureStarted();
    List<Expectation> registered = new ArrayList<>(responses.length);
    for (HttpResponse resp : responses) {
      Expectation[] created = client.when(current, Times.exactly(1)).respond(resp);
      for (Expectation e : created) {
        registered.add(e);
      }
    }
    current = null;
    times = Times.unlimited();
    return new Registration(client, registered.toArray(new Expectation[0]));
  }

  private Registration respond(HttpResponse response) {
    ensureStarted();
    Expectation[] created = client.when(current, times).respond(response);
    current = null;
    times = Times.unlimited();
    return new Registration(client, created);
  }

  private void ensureStarted() {
    if (current == null) {
      throw new IllegalStateException(
          "Call get/post/put/delete/method(...) first before adding matchers or responding.");
    }
  }
}
