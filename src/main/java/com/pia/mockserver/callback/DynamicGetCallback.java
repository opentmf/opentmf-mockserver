package com.pia.mockserver.callback;

import static com.pia.mockserver.model.Error.createErrorContextForNotFound;
import static com.pia.mockserver.model.TmfConstants.*;
import static com.pia.mockserver.util.AuditFieldUtil.*;
import static com.pia.mockserver.util.ErrorResponseUtil.getErrorResponse;
import static com.pia.mockserver.util.HttpRequestUtil.extractFields;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pia.mockserver.model.TmfStatePath;
import com.pia.mockserver.util.JacksonUtil;
import com.pia.mockserver.util.PathExtractor;
import com.pia.mockserver.util.PayloadCache;
import java.util.Objects;
import java.util.Set;
import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.model.MediaType;

/**
 * Represents an implementation of the ExpectationResponseCallback interface to handle incoming HTTP
 * GET requests and generate appropriate responses for dynamic GET requests in the MockServer. This
 * class is responsible for processing the GET request, retrieving the cached data, applying
 * necessary transformations or updates, and generating a response.
 *
 * @author Yusuf BOZKURT
 */
public class DynamicGetCallback implements ExpectationResponseCallback {

  private static final PayloadCache CACHE = PayloadCache.getInstance();

  /**
   * Handles incoming HTTP GET requests and generates appropriate responses for dynamic GET requests
   * in the MockServer. This method is invoked by the MockServer when a dynamic GET request is
   * received, and it is responsible for processing the request, retrieving the cached data,
   * applying necessary transformations or updates, and generating a response.
   *
   * <p>Upon receiving a dynamic GET request, this method extracts the domain and ID from the
   * request path, resolves the appropriate {@link TmfStatePath TmfStatePath}
   * based on the domain, and retrieves the cached data associated with the domain and ID. If the
   * cached data is not found, it returns a not found response with an appropriate error message.
   *
   * <p>If the cached data is found, it checks if any state transition is required based on the
   * TmfStatePath. If so, it updates the state of the cached data and sets the update fields using
   * {@link
   * com.pia.mockserver.util.AuditFieldUtil#setUpdateFields(ObjectNode)
   * AuditFieldUtil.setUpdateFields}.
   *
   * <p>It then touches the cache to update the last access time of the cached data and extracts any
   * specified fields from the request. It filters the cached data based on the extracted fields and
   * generates a response containing the filtered data.
   *
   * <p>This method is essential for simulating dynamic GET endpoints in the service during testing,
   * allowing developers to verify endpoint behavior and data retrieval under various scenarios. By
   * using this method, developers can thoroughly test the service's functionality and ensure it
   * retrieves and processes data correctly in different situations.
   *
   * @param httpRequest The incoming HTTP request to be handled.
   * @return The generated HTTP response to be sent back to the client.
   */
  @Override
  public HttpResponse handle(HttpRequest httpRequest) {
    // Extract the domain and ID from the request path
    String domain = PathExtractor.extractDomainWithId(httpRequest.getPath().getValue());
    String id = PathExtractor.extractLastPart(httpRequest.getPath().getValue());

    // Resolve the appropriate TmfStatePath based on the domain
    TmfStatePath tmfStatePath = TmfStatePath.resolveFromPath(domain);

    // Retrieve the cached data associated with the domain and ID
    JsonNode cachedData = CACHE.get(domain, id);

    // If cached data is not found, return a not found response
    if (Objects.isNull(cachedData)) {
      return getErrorResponse(HttpStatusCode.NOT_FOUND_404, createErrorContextForNotFound());
    }

    // Check if state transition is required based on TmfStatePath, and update cached data if
    // necessary
    if (needToPatch(tmfStatePath, cachedData)) {
      ((ObjectNode) cachedData).put(tmfStatePath.getVariableName(), tmfStatePath.getFinalState());
      setUpdateFields((ObjectNode) cachedData);
    }

    // Update the last access time of cached data in the cache
    CACHE.touch(domain, id);

    // Extract specified fields from the request
    Set<String> fields = extractFields(httpRequest);

    // Filter the cached data based on the extracted fields
    JsonNode filteredData = filterFields(cachedData, fields);

    // Generate and return the response containing the filtered data
    return HttpResponse.response()
        .withStatusCode(HttpStatusCode.OK_200.code())
        .withContentType(MediaType.APPLICATION_JSON)
        .withBody(JacksonUtil.writeAsString(filteredData));
  }

  /**
   * Filters the fields of the original JSON node based on the specified field names.
   *
   * @param originalNode The original JSON node to be filtered.
   * @param fieldNames The set of field names to be included in the filtered JSON.
   * @return The filtered JSON node containing only the specified fields.
   */
  private JsonNode filterFields(JsonNode originalNode, Set<String> fieldNames) {
    if (fieldNames.isEmpty()) {
      return originalNode;
    }
    ObjectNode filteredNode = JacksonUtil.createObjectNode();
    for (String fieldName : fieldNames) {
      if (originalNode.has(fieldName)) {
        filteredNode.set(fieldName, originalNode.get(fieldName));
      }
    }
    return filteredNode;
  }

  /**
   * Checks if a state transition is required based on the TmfStatePath and the current state of the
   * cached data.
   *
   * @param tmfStatePath The resolved TmfStatePath for the domain.
   * @param cachedData The cached data associated with the domain and ID.
   * @return true if a state transition is required, false otherwise.
   */
  private boolean needToPatch(TmfStatePath tmfStatePath, JsonNode cachedData) {
    return !(cachedData.has(UPDATED_DATE) || cachedData.has(UPDATED_BY))
        && cachedData.has(tmfStatePath.getVariableName())
        && cachedData
            .get(tmfStatePath.getVariableName())
            .asText()
            .equals(tmfStatePath.getInitialState());
  }
}
