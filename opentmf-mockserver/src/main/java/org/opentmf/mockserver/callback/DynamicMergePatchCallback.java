package org.opentmf.mockserver.callback;

import static org.opentmf.mockserver.util.AuditFieldUtil.setUpdateFields;
import static org.opentmf.mockserver.util.ErrorResponseUtil.getErrorResponse;

import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.model.MediaType;
import org.opentmf.commons.patch.JsonMergePatch;
import org.opentmf.mockserver.model.RequestContext;
import org.opentmf.mockserver.util.IdempotencyGuard;
import org.opentmf.mockserver.util.JacksonUtil;
import org.opentmf.mockserver.util.PayloadCache;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 *
 *
 * <h2>DynamicMergePatchCallback</h2>
 *
 * <ul>
 *   <li>Considers the last path parameter as the id.
 *   <li>Allows either `:(version=XYZ)` or `?version=XYZ` for specifying the version for versioned
 *       entities
 *   <li>Checks if a payload is found in the cache with that id (and version if versioned entity).
 *   <li>Returns 404 if no payload is cached with that id.
 *   <li>Applies the mergePatch body to the cached payload.
 *   <li>Updates the cached payload with the patch result and restarts the cache evict timer.
 *   <li>Adds/overrides updatedDate, updatedBy fields, plus, increases the revision field's value by
 *       one.
 *   <li>Returns 200 and the updated payload.
 * </ul>
 *
 * @author Yusuf BOZKURT
 */
public class DynamicMergePatchCallback implements ExpectationResponseCallback {

  private static final PayloadCache CACHE = PayloadCache.getInstance();

  @Override
  public HttpResponse handle(HttpRequest httpRequest) {
    ExistingEntryLoader.Result pre = ExistingEntryLoader.load(httpRequest);
    if (pre.isShortCircuit()) {
      return pre.shortCircuit();
    }
    RequestContext ctx = pre.ctx();
    JsonNode cachedData = pre.cachedData();

    String body = httpRequest.getBodyAsString();
    JsonMergePatch mergePatch = JsonMergePatch.fromJson(body);

    JsonNode patchedNode;
    try {
      patchedNode = mergePatch.apply(cachedData);
    } catch (Exception e) {
      return getErrorResponse(HttpStatusCode.BAD_REQUEST_400, e.getMessage());
    }

    setUpdateFields((ObjectNode) patchedNode);

    CACHE.update(ctx, patchedNode);

    HttpResponse response =
        HttpResponse.response()
            .withStatusCode(HttpStatusCode.OK_200.code())
            .withContentType(MediaType.APPLICATION_JSON)
            .withBody(JacksonUtil.writeAsString(patchedNode));
    IdempotencyGuard.store(httpRequest, response, ctx);
    return response;
  }
}
