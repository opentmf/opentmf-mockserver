package org.opentmf.mockserver.callback;

import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;
import org.opentmf.mockserver.model.RequestContext;
import org.opentmf.mockserver.util.IdempotencyGuard;
import org.opentmf.mockserver.util.PayloadCache;

/**
 *
 *
 * <h2>DynamicDeleteCallback</h2>
 *
 * <ul>
 *   <li>Considers the last path parameter as the id.
 *   <li>Allows either <code>:(version=XYZ)</code> or <code>?version=XYZ</code> for specifying the
 *       version for versioned entities
 *   <li>Checks if a payload is found in the cache with that id (and version if versioned entity).
 *   <li>Returns 404 if no payload is cached with that id.
 *   <li>Removes the cached payload from the cache, with that id.
 *   <li>Returns 204 No Content.
 * </ul>
 *
 * @author Yusuf BOZKURT
 */
public class DynamicDeleteCallback implements ExpectationResponseCallback {

  private static final PayloadCache CACHE = PayloadCache.getInstance();

  @Override
  public HttpResponse handle(HttpRequest httpRequest) {
    ExistingEntryLoader.Result pre = ExistingEntryLoader.load(httpRequest);
    if (pre.isShortCircuit()) {
      return pre.shortCircuit();
    }
    RequestContext ctx = pre.ctx();

    HttpResponse response =
        HttpResponse.response().withStatusCode(HttpStatusCode.NO_CONTENT_204.code());
    // Store the idempotency record before clearing so it stays linked to a still-present
    // resource; PayloadCache.clear is a manual removal and does not trigger Option A eviction,
    // so the record outlives the delete and lets retries reply 204 instead of 404.
    IdempotencyGuard.store(httpRequest, response, ctx);
    CACHE.clear(ctx);
    return response;
  }
}
