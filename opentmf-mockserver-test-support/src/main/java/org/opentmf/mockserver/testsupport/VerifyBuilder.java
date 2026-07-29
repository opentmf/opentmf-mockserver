package org.opentmf.mockserver.testsupport;

import static org.mockserver.model.HttpRequest.request;

import org.mockserver.client.MockServerClient;
import org.mockserver.matchers.MatchType;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.JsonBody;
import org.mockserver.verify.VerificationTimes;

/**
 * Fluent verification builder. Wraps {@link MockServerClient#verify(HttpRequest, VerificationTimes)}
 * so consumers stop importing {@code org.mockserver.verify.VerificationTimes} and
 * {@code org.mockserver.matchers.Times} directly.
 *
 * <pre>{@code
 * mock.verify().post("/document").times(1);
 * mock.verify().get("/kba/abc").withHeader("Authorization", "Bearer .*").atLeast(1);
 * mock.verify().delete("/document/42").never();
 * }</pre>
 */
public class VerifyBuilder {

  private final MockServerClient client;
  private HttpRequest current;

  VerifyBuilder(MockServerClient client) {
    this.client = client;
  }

  /** Start verifying a {@code GET <path>} call. */
  public VerifyBuilder get(String path) {
    return method("GET", path);
  }

  /** Start verifying a {@code POST <path>} call. */
  public VerifyBuilder post(String path) {
    return method("POST", path);
  }

  /** Start verifying a {@code PUT <path>} call. */
  public VerifyBuilder put(String path) {
    return method("PUT", path);
  }

  /** Start verifying a {@code DELETE <path>} call. */
  public VerifyBuilder delete(String path) {
    return method("DELETE", path);
  }

  /** Start verifying a call with an arbitrary method + path. */
  public VerifyBuilder method(String method, String path) {
    this.current = request().withMethod(method).withPath(path);
    return this;
  }

  /** Additional matcher: recorded call must have {@code header: value}. */
  public VerifyBuilder withHeader(String name, String value) {
    ensureStarted();
    current.withHeader(name, value);
    return this;
  }

  /** Additional matcher: recorded call body must match the JSON object at the given expression. */
  public VerifyBuilder withJsonBody(String jsonBody) {
    ensureStarted();
    current.withBody(new JsonBody(jsonBody, MatchType.ONLY_MATCHING_FIELDS));
    return this;
  }

  /** Additional matcher: recorded call must have {@code queryParam=value}. */
  public VerifyBuilder withQueryParam(String name, String value) {
    ensureStarted();
    current.withQueryStringParameter(name, value);
    return this;
  }

  /** Assert the matching call happened exactly {@code n} times. */
  public void times(int n) {
    verify(VerificationTimes.exactly(n));
  }

  /** Assert the matching call happened at least {@code n} times. */
  public void atLeast(int n) {
    verify(VerificationTimes.atLeast(n));
  }

  /** Assert the matching call happened at most {@code n} times. */
  public void atMost(int n) {
    verify(VerificationTimes.atMost(n));
  }

  /** Assert the matching call was never made. */
  public void never() {
    verify(VerificationTimes.exactly(0));
  }

  /** Assert the matching call happened at least once. */
  public void once() {
    times(1);
  }

  private void verify(VerificationTimes times) {
    ensureStarted();
    client.verify(current, times);
    current = null;
  }

  private void ensureStarted() {
    if (current == null) {
      throw new IllegalStateException(
          "Call get/post/put/delete/method(...) first before adding matchers or asserting.");
    }
  }
}
