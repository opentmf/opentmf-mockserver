package com.pia.mockserver.callback;

import static com.pia.mockserver.model.Error.createErrorContextForNotFound;
import static com.pia.mockserver.util.AuditFieldUtil.setUpdateFields;
import static com.pia.mockserver.util.ErrorResponseUtil.getErrorResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.jsonpatch.JsonPatchException;
import com.github.fge.jsonpatch.mergepatch.JsonMergePatch;
import com.pia.mockserver.model.Id;
import com.pia.mockserver.model.TmfStatePath;
import com.pia.mockserver.util.IdExtractor;
import com.pia.mockserver.util.JacksonUtil;
import com.pia.mockserver.util.PathExtractor;
import com.pia.mockserver.util.PayloadCache;
import java.util.Objects;
import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.model.MediaType;

/**
 * Represents an implementation of the ExpectationResponseCallback interface to handle incoming HTTP
 * PATCH requests using JSON Merge Patch to update a specific record and generate appropriate
 * responses for dynamic PATCH requests in the MockServer.
 *
 * <p>This class is responsible for processing the request, applying the JSON Merge Patch to the
 * corresponding record in the cache, and generating a response indicating the success or failure of
 * the update operation.
 *
 * @author Yusuf BOZKURT
 */
public class DynamicMergePatchCallback implements ExpectationResponseCallback {
  private static final PayloadCache CACHE = PayloadCache.getInstance();

  /**
   * Handles incoming HTTP PATCH requests using JSON Merge Patch to update a specific record and
   * generates appropriate responses for dynamic PATCH requests in the MockServer. This method is
   * invoked by the MockServer when a dynamic PATCH request is received, and it is responsible for
   * processing the request, applying the JSON Merge Patch to the corresponding record in the cache,
   * and generating a response indicating the success or failure of the update operation.
   *
   * <p>Upon receiving a dynamic PATCH request, this method extracts the domain and ID of the record
   * to be updated from the request path. It then retrieves the cached data associated with the
   * domain and ID from the payload cache. If the data is found in the cache, indicating that the
   * record exists, the JSON Merge Patch provided in the request body is applied to the cached data,
   * and the updated data is stored back in the cache. A successful update response (HTTP 200 OK)
   * containing the updated data is returned. If the data is not found in the cache, indicating that
   * the record does not exist, an error response (HTTP 404 Not Found) is returned. If there is an
   * error while applying the JSON Merge Patch, such as invalid patch data, an error response (HTTP
   * 400 Bad Request) is returned.
   *
   * <p>This method is essential for simulating dynamic PATCH endpoints in the service during
   * testing, allowing developers to verify endpoint behavior and record updates under various
   * scenarios. By using JSON Merge Patch, developers can provide partial updates to records, which
   * is useful for updating specific fields without sending the entire record payload.
   *
   * @param httpRequest The incoming HTTP request to be handled.
   * @return The generated HTTP response indicating the success or failure of the update operation.
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

    // If the data exists in the cache, indicating that the record exists, apply the JSON Merge
    // Patch provided in the request body
    // and store the updated data back in the cache
    if (Objects.nonNull(cachedData)) {
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
      CACHE.update(domain, id.getCompositeId(), patchedNode);

      // Set audit fields for update operation
      setUpdateFields((ObjectNode) patchedNode);

      // Return a successful update response (HTTP 200 OK) containing the updated data
      return HttpResponse.response()
          .withStatusCode(HttpStatusCode.OK_200.code())
          .withContentType(MediaType.APPLICATION_JSON)
          .withBody(JacksonUtil.writeAsString(patchedNode));
    }

    // If the data does not exist in the cache, indicating that the record does not exist, return an
    // error response (HTTP 404 Not Found)
    return getErrorResponse(HttpStatusCode.NOT_FOUND_404, createErrorContextForNotFound());
  }
}
