package org.opentmf.mockserver.callback;

import static org.opentmf.mockserver.model.Error.createErrorContextForNotFound;
import static org.opentmf.mockserver.util.ErrorResponseUtil.getErrorResponse;

import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import java.io.IOException;
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
 * <h2>DynamicJsonPatchCallback</h2>
 *
 * <ul>
 *   <li>Considers the last path parameter as the id.
 *   <li>Allows either `:(version=XYZ)` or `?version=XYZ` for specifying the version for versioned entities
 *   <li>Checks if a payload is found in the cache with that id (and version if versioned entity).
 *   <li>Returns 404 if no payload is cached with that id.
 *   <li>Applies the jsonPatch body to the cached payload.
 *   <li>Updates the cached payload with the patch result and restarts the cache evict timer.
 *   <li>Adds/overrides updatedDate, updatedBy fields, plus, increases the revision field's value by one.
 *   <li>Returns 200 and the updated payload.
 * </ul>
 *
 * @author Yusuf BOZKURT
 */
public class DynamicJsonPatchCallback implements ExpectationResponseCallback {

  private static final PayloadCache CACHE = PayloadCache.getInstance();

  @Override
  public HttpResponse handle(HttpRequest httpRequest) {
    RequestContext ctx = RequestContext.initialize(httpRequest, true, null);

    // Retrieve the cached data associated with the domain and ID
    JsonNode cachedData = ctx.usePointQuery()
        ? CACHE.get(ctx)
        : CACHE.getLatestOf(ctx);

    // If the data does not exist in the cache, indicating that the resource does not exist, return
    // a not found response
    if (Objects.isNull(cachedData)) {
      return getErrorResponse(HttpStatusCode.NOT_FOUND_404, createErrorContextForNotFound());
    }

    ctx.obtainVersionFromPayloadIfNecessary(cachedData);

    // Extract the JSON patch data from the request body
    String patchData = httpRequest.getBodyAsString();
    JsonNode patchedNode;
    try {
      // Apply the JSON patch to the cached data
      patchedNode = applyPatch(cachedData, patchData);
    } catch (Exception e) {
      // If the patch application fails, return a bad request response with the error message
      return getErrorResponse(HttpStatusCode.BAD_REQUEST_400, e.getMessage());
    }

    // Update the cached data with the patched data
    CACHE.update(ctx, patchedNode);

    // Return a successful response with the patched data
    return HttpResponse.response()
        .withStatusCode(HttpStatusCode.OK_200.code())
        .withContentType(MediaType.APPLICATION_JSON)
        .withBody(JacksonUtil.writeAsString(patchedNode));
  }

  /**
   * Applies the provided JSON patch to the given JSON data.
   *
   * @param cachedData The original JSON data to be patched.
   * @param patchData The JSON patch data to apply.
   * @return The JSON node representing the patched data.
   * @throws IOException If an I/O error occurs.
   * @throws JsonPatchException If an error occurs while applying the JSON patch.
   */
  private JsonNode applyPatch(JsonNode cachedData, String patchData)
      throws IOException, JsonPatchException {
    // Parse the patch data to JsonNode
    JsonNode patchNode = JacksonUtil.readAsTree(patchData);
    // Create the JSON patch
    JsonPatch patch = JsonPatch.fromJson(patchNode);
    // Apply the patch to the cached data
    TreeNode patchedObjNode = patch.apply(cachedData);
    // Convert the patched node to a JSON node
    return JacksonUtil.convertValue(patchedObjNode, JsonNode.class);
  }
}
