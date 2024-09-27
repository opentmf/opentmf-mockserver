package com.pia.mockserver.callback;

import static com.pia.mockserver.model.Error.createErrorContextForNotFound;
import static com.pia.mockserver.util.ErrorResponseUtil.getErrorResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.pia.mockserver.util.PathExtractor;
import com.pia.mockserver.util.PayloadCache;
import java.util.Objects;
import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;

/**
 * Represents an implementation of the ExpectationResponseCallback interface to handle incoming HTTP
 * DELETE requests for deleting a resource dynamically in the MockServer. This class is responsible
 * for processing the DELETE request, deleting the corresponding resource from the cache, and
 * generating appropriate responses.
 *
 * @author Yusuf BOZKURT
 */
public class DynamicDeleteCallback implements ExpectationResponseCallback {

  private static final PayloadCache CACHE = PayloadCache.getInstance();

  /**
   * Handles incoming HTTP DELETE requests for deleting a resource and generates appropriate
   * responses for dynamic DELETE requests in the MockServer. This method is invoked by the
   * MockServer when a dynamic DELETE request is received, and it is responsible for processing the
   * request, deleting the corresponding resource from the cache, and generating a response
   * indicating the success or failure of the deletion operation.
   *
   * <p>Upon receiving a dynamic DELETE request, this method extracts the domain and ID of the
   * resource to be deleted from the request path. It then attempts to retrieve the cached data
   * associated with the domain and ID from the payload cache. If the data is found in the cache,
   * indicating that the resource exists, it is removed from the cache, and a successful deletion
   * response (HTTP 204 No Content) is returned. If the data is not found in the cache, indicating
   * that the resource does not exist, an error response (HTTP 404 Not Found) is returned.
   *
   * <p>This method is essential for simulating dynamic DELETE endpoints in the service during
   * testing, allowing developers to verify endpoint behavior and resource deletion under various
   * scenarios. By using this method, developers can ensure that the service correctly handles
   * DELETE requests and removes resources from the cache as expected.
   *
   * @param httpRequest The incoming HTTP request to be handled.
   * @return The generated HTTP response indicating the success or failure of the deletion
   *     operation.
   */
  @Override
  public HttpResponse handle(HttpRequest httpRequest) {
    // Extract the domain and ID of the resource from the request path
    String domain = PathExtractor.extractDomainWithId(httpRequest.getPath().getValue());
    String id = PathExtractor.extractLastPart(httpRequest.getPath().getValue());

    // Retrieve the cached data associated with the domain and ID from the payload cache
    JsonNode cachedData = CACHE.get(domain, id);

    // If the data exists in the cache, indicating that the resource exists, delete it from the
    // cache and return a successful deletion response
    if (Objects.nonNull(cachedData)) {
      CACHE.clear(domain, id);
      return HttpResponse.response().withStatusCode(HttpStatusCode.NO_CONTENT_204.code());
    }

    // If the data does not exist in the cache, indicating that the resource does not exist, return
    // an error response indicating resource not found
    return getErrorResponse(HttpStatusCode.NOT_FOUND_404, createErrorContextForNotFound());
  }
}
