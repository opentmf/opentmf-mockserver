package org.opentmf.mockserver.callback;

import static org.opentmf.mockserver.model.Error.createErrorContextForNotFound;
import static org.opentmf.mockserver.util.ErrorResponseUtil.getErrorResponse;

import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import org.opentmf.mockserver.model.Id;
import org.opentmf.mockserver.model.TmfStatePath;
import org.opentmf.mockserver.util.IdExtractor;
import org.opentmf.mockserver.util.JacksonUtil;
import org.opentmf.mockserver.util.PathExtractor;
import org.opentmf.mockserver.util.PayloadCache;
import java.io.IOException;
import java.util.Objects;
import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.model.MediaType;

/**
 * Represents an implementation of the ExpectationResponseCallback interface to handle incoming HTTP
 * PATCH requests for applying JSON patches to resources and generating appropriate responses for
 * dynamic PATCH requests in the MockServer.
 *
 * <p>This class is responsible for processing the request, applying the JSON patch to the
 * corresponding resource, and generating a response indicating the success or failure of the patch
 * operation.
 *
 * @author Yusuf BOZKURT
 */
public class DynamicJsonPatchCallback implements ExpectationResponseCallback {

  private static final PayloadCache CACHE = PayloadCache.getInstance();

  /**
   * Handles incoming HTTP PATCH requests for applying JSON patches to resources and generates
   * appropriate responses for dynamic PATCH requests in the MockServer. This method is invoked by
   * the MockServer when a dynamic PATCH request is received, and it is responsible for processing
   * the request, applying the JSON patch to the corresponding resource, and generating a response
   * indicating the success or failure of the patch operation.
   *
   * <p>Upon receiving a dynamic PATCH request, this method extracts the domain and ID of the
   * resource to be patched from the request path. It then attempts to retrieve the cached data
   * associated with the domain and ID from the payload cache. If the data is found in the cache,
   * indicating that the resource exists, the JSON patch provided in the request body is applied to
   * the cached data. If the patch application is successful, the patched data is updated in the
   * cache, and a successful response (HTTP 200 OK) containing the patched data is returned. If the
   * patch application fails due to invalid patch data or other errors, an error response (HTTP 400
   * Bad Request) is returned. If the data is not found in the cache, indicating that the resource
   * does not exist, an error response (HTTP 404 Not Found) is returned.
   *
   * <p>This method is essential for simulating dynamic PATCH endpoints in the service during
   * testing, allowing developers to verify endpoint behavior and resource patching under various
   * scenarios. By using this method, developers can ensure that the service correctly handles PATCH
   * requests and applies JSON patches to resources as expected.
   *
   * @param httpRequest The incoming HTTP request to be handled.
   * @return The generated HTTP response indicating the success or failure of the patch operation.
   */
  @Override
  public HttpResponse handle(HttpRequest httpRequest) {
    String domain = PathExtractor.extractDomainWithId(httpRequest.getPath().getValue());
    TmfStatePath tmfStatePath = TmfStatePath.resolveFromPath(domain);
    Id id = IdExtractor.parseId(PathExtractor.extractLastPart(httpRequest.getPath().getValue()));

    // Retrieve the cached data associated with the domain and ID
    JsonNode cachedData = (id.isProvided() || !tmfStatePath.isVersioned())
        ? CACHE.get(domain, id.getCompositeId())
        : CACHE.getLatestOf(domain, id.getId());

    // If the data does not exist in the cache, indicating that the resource does not exist, return
    // a not found response
    if (Objects.isNull(cachedData)) {
      return getErrorResponse(HttpStatusCode.NOT_FOUND_404, createErrorContextForNotFound());
    }

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
    CACHE.update(domain, id.getCompositeId(), patchedNode);

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
