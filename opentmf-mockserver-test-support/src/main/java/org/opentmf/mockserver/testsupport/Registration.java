package org.opentmf.mockserver.testsupport;

import java.util.Arrays;
import java.util.List;
import org.mockserver.client.MockServerClient;
import org.mockserver.mock.Expectation;

/**
 * Handle returned by {@link StubBuilder} {@code respond*} methods and
 * {@link MockServerSupport#expect(org.mockserver.model.HttpRequest, org.mockserver.model.HttpResponse)}.
 * Carries the expectation id(s) MockServer minted for the registration and provides
 * targeted cleanup via {@link #clear()}, avoiding a full-server reset just to remove
 * one stub.
 *
 * <p>Typical use:
 *
 * <pre>{@code
 * Registration flaky = mock.stub().get("/flaky").limit(3).respondStatus(500);
 * // ... exercise the retry path 3 times, they all get 500 ...
 * flaky.clear();
 * mock.stub().get("/flaky").respondStatus(200);
 * // ... final call now succeeds ...
 * }</pre>
 *
 * <p>Sequences (via {@link StubBuilder#respondSequence(int...)}) register one expectation
 * per element; {@link #ids()} returns every id, {@link #id()} returns the first.
 */
public final class Registration {

  private final MockServerClient client;
  private final List<String> ids;

  Registration(MockServerClient client, Expectation[] expectations) {
    this.client = client;
    this.ids = Arrays.stream(expectations).map(Expectation::getId).toList();
  }

  /** First expectation id. For sequences, {@link #ids()} returns them all. */
  public String id() {
    return ids.isEmpty() ? null : ids.get(0);
  }

  /** All expectation ids in registration order. */
  public List<String> ids() {
    return ids;
  }

  /**
   * Remove the expectation(s) registered by this call from the MockServer.
   * Idempotent — a second call is a no-op.
   */
  public void clear() {
    for (String id : ids) {
      client.clear(id);
    }
  }
}
