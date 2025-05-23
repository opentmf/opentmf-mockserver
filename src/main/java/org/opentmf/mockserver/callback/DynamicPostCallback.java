package org.opentmf.mockserver.callback;

import static org.opentmf.mockserver.model.TmfConstants.HREF;
import static org.opentmf.mockserver.model.TmfConstants.UPDATED_BY;
import static org.opentmf.mockserver.model.TmfConstants.UPDATED_DATE;
import static org.opentmf.mockserver.util.AuditFieldUtil.setCreateFields;
import static org.opentmf.mockserver.util.ErrorResponseUtil.getErrorResponse;
import static org.opentmf.mockserver.util.PathExtractor.extractDomainWithoutId;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.opentmf.mockserver.model.Id;
import org.opentmf.mockserver.model.TmfStatePath;
import org.opentmf.mockserver.util.AuditFieldUtil;
import org.opentmf.mockserver.util.ErrorResponseUtil;
import org.opentmf.mockserver.util.IdExtractor;
import org.opentmf.mockserver.util.JacksonUtil;
import org.opentmf.mockserver.util.PathExtractor;
import org.opentmf.mockserver.util.PayloadCache;
import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.model.MediaType;

/**
 * Callback implementation for handling dynamic POST requests in the MockServer. This callback is
 * responsible for processing dynamic POST requests, generating responses, and managing payload
 * caching. It is used to simulate the behavior of dynamic POST endpoints in the service.
 *
 * <p>When a dynamic POST request is received, this callback parses the request body, generates a
 * response based on the payload and request path, and caches the payload for future reference. If
 * the request body contains an ID that already exists in the cache, a bad request response is
 * returned with an appropriate error message.
 *
 * <p>The generated response includes the processed payload with additional fields such as ID, HREF,
 * and initial state if they are not already present. Any 'updatedBy' and 'updatedDate' fields in
 * the payload are removed before generating the response to ensure they are not modified.
 *
 * <p>This callback is essential for mocking dynamic POST endpoints in the service, allowing
 * thorough testing of endpoint behavior and payload handling. By using this callback, developers
 * can simulate various scenarios and ensure the service behaves correctly under different
 * conditions.
 *
 * <p>Note: This callback relies on other utility classes such as {@link
 * JacksonUtil JacksonUtil}, {@link PayloadCache
 * PayloadCache}, {@link AuditFieldUtil AuditFieldUtil}, {@link
 * ErrorResponseUtil ErrorResponseUtil}, and {@link
 * PathExtractor PathExtractor} for JSON parsing, payload caching, error
 * response generation, and path extraction functionalities.
 *
 * <p>This class implements the {@link ExpectationResponseCallback
 * ExpectationResponseCallback} interface, which defines the method {@link
 * ExpectationResponseCallback#handle(HttpRequest) handle(HttpRequest)},
 * used to handle incoming HTTP requests and generate appropriate responses.
 *
 * @author Gokhan Demir
 */
public class DynamicPostCallback implements ExpectationResponseCallback {

  private static final PayloadCache CACHE = PayloadCache.getInstance();

  /**
   * Handles incoming HTTP requests and generates appropriate responses for dynamic POST requests in
   * the MockServer. This method is invoked by the MockServer when a dynamic POST request is
   * received, and it is responsible for processing the request, generating a response, and managing
   * payload caching.
   *
   * <p>Upon receiving a dynamic POST request, this method extracts the domain from the request
   * path, resolves the appropriate {@link TmfStatePath TmfStatePath} based on
   * the domain, and parses the request body. It then checks if the payload contains an ID that
   * already exists in the cache. If so, it returns a bad request response with an appropriate error
   * message. Otherwise, it generates a response based on the payload and request path, including
   * additional fields such as ID, HREF, and initial state if necessary.
   *
   * <p>After generating the response, it removes any 'updatedBy' and 'updatedDate' fields from the
   * payload to prevent modification, sets the create fields using {@link
   * AuditFieldUtil#setCreateFields(ObjectNode)
   * AuditFieldUtil.setCreateFields}, caches the payload using {@link
   * PayloadCache PayloadCache}, and returns the response to the client.
   *
   * <p>This method is essential for simulating dynamic POST endpoints in the service during
   * testing, allowing developers to verify endpoint behavior and payload handling under various
   * scenarios. By using this method, developers can thoroughly test the service's functionality and
   * ensure it behaves correctly in different situations.
   *
   * @param httpRequest The incoming HTTP request to be handled.
   * @return The generated HTTP response to be sent back to the client.
   */
  @Override
  public HttpResponse handle(HttpRequest httpRequest) {
    // Extract the domain from the request path
    String domain = extractDomainWithoutId(httpRequest.getPath().getValue());

    // Resolve the appropriate TmfStatePath based on the domain
    TmfStatePath tmfStatePath = TmfStatePath.resolveFromPath(domain);

    // Parse the request body
    String body = httpRequest.getBodyAsString();
    ObjectNode parsedBody = (ObjectNode) JacksonUtil.readAsTree(body);

    // Check if the payload contains an ID that already exists in the cache
    Id id = IdExtractor.extractId(tmfStatePath.isVersioned(), parsedBody);
    if (id.isProvided() && CACHE.get(domain, id.getCompositeId()) != null) {
      return getErrorResponse(
          HttpStatusCode.BAD_REQUEST_400, "id = " + id.getCompositeId() + " already exists.");
    }

    // Generate a new ID if not already present in the payload
    parsedBody.put(HREF, httpRequest.getPath().getValue() + "/" + id.getHrefSuffix());

    // Set initial state if not already present in the payload
    if (!parsedBody.has(tmfStatePath.getVariableName())) {
      parsedBody.put(tmfStatePath.getVariableName(), tmfStatePath.getInitialState());
    }

    // Remove 'updatedBy' and 'updatedDate' fields from the payload
    removeUpdateFieldIfExist(parsedBody);

    // Set create fields using AuditFieldUtil
    setCreateFields(parsedBody);

    // Generate response JSON
    String responseJson = JacksonUtil.writeAsString(parsedBody);

    CACHE.put(domain, id.getCompositeId(), parsedBody);
    return HttpResponse.response()
        .withStatusCode(HttpStatusCode.OK_200.code())
        .withContentType(MediaType.APPLICATION_JSON)
        .withBody(responseJson);
  }

  private void removeUpdateFieldIfExist(ObjectNode objectNode) {
    objectNode.remove(UPDATED_BY);
    objectNode.remove(UPDATED_DATE);
  }
}
