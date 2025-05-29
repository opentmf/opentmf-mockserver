package org.opentmf.mockserver.callback;

import static org.opentmf.mockserver.model.Error.createErrorContextForNotFound;
import static org.opentmf.mockserver.util.AuditFieldUtil.setUpdateFields;
import static org.opentmf.mockserver.util.ErrorResponseUtil.getErrorResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.jsonpatch.JsonPatchException;
import com.github.fge.jsonpatch.mergepatch.JsonMergePatch;
import java.util.Objects;
import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.model.MediaType;
import org.opentmf.mockserver.model.RequestContext;
import org.opentmf.mockserver.util.JacksonUtil;
import org.opentmf.mockserver.util.PayloadCache;

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
    RequestContext ctx = RequestContext.initialize(httpRequest, true, null);

    // Retrieve the cached data associated with the domain and ID
    JsonNode cachedData = ctx.usePointQuery() ? CACHE.get(ctx) : CACHE.getLatestOf(ctx);

    // If the data does not exist in the cache, indicating that the resource does not exist, return
    // a not found response
    if (Objects.isNull(cachedData)) {
      return getErrorResponse(HttpStatusCode.NOT_FOUND_404, createErrorContextForNotFound());
    }

    ctx.obtainVersionFromPayloadIfNecessary(cachedData);

    // Extract the JSON Merge Patch from the request body
    String body = httpRequest.getBodyAsString();
    JsonMergePatch patchData = JacksonUtil.readAsJsonMerger(body);

    // Apply the JSON Merge Patch to the cached data
    JsonNode patchedNode;
    try {
      patchedNode = patchData.apply(cachedData);
    } catch (JsonPatchException e) {
      // If there is an error while applying the JSON Merge Patch, return an error response (HTTP
      // 400 Bad Request)
      return getErrorResponse(HttpStatusCode.BAD_REQUEST_400, e.getMessage());
    }

    // Update the cached data with the patched node
    CACHE.update(ctx, patchedNode);

    // Set audit fields for update operation
    setUpdateFields((ObjectNode) patchedNode);

    // Return a successful update response (HTTP 200 OK) containing the updated data
    return HttpResponse.response()
        .withStatusCode(HttpStatusCode.OK_200.code())
        .withContentType(MediaType.APPLICATION_JSON)
        .withBody(JacksonUtil.writeAsString(patchedNode));
  }
}
