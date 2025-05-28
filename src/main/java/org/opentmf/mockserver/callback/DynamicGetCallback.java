package org.opentmf.mockserver.callback;

import static org.opentmf.mockserver.model.Error.createErrorContextForNotFound;
import static org.opentmf.mockserver.model.TmfConstants.HREF;
import static org.opentmf.mockserver.model.TmfConstants.ID;
import static org.opentmf.mockserver.model.TmfConstants.UPDATED_BY;
import static org.opentmf.mockserver.model.TmfConstants.UPDATED_DATE;
import static org.opentmf.mockserver.util.AuditFieldUtil.setUpdateFields;
import static org.opentmf.mockserver.util.ErrorResponseUtil.getErrorResponse;
import static org.opentmf.mockserver.util.HttpRequestUtil.extractFields;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Objects;
import java.util.Set;
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
 * <h2>DynamicGetCallback</h2>
 *
 * <ul>
 *   <li>Considers the last path parameter as the id.
 *   <li>Allows either `:(version=XYZ)` or `?version=XYZ` for specifying the version for versioned
 *       entities
 *   <li>Checks if a payload is found in the cache with that id (and version if versioned entity).
 *   <li>Returns 404 if no payload is cached with that id.
 *   <li>If the cached payload is not previously patched, and if its state field is still at initial
 *       value, then sets the final value to the state field, and adds updatedDate, updatedBy
 *       fields, plus, increases the revision field.
 *   <li>Touches the cache, so that the eviction timer restarts for this particular payload.
 *   <li>Returns 200 and the potentially manipulated payload.
 * </ul>
 *
 * @author Yusuf BOZKURT
 */
public class DynamicGetCallback implements ExpectationResponseCallback {

  private static final PayloadCache CACHE = PayloadCache.getInstance();

  @Override
  public HttpResponse handle(HttpRequest httpRequest) {
    RequestContext ctx = RequestContext.initialize(httpRequest, true, null);

    // Retrieve the cached data associated with the domain and ID
    JsonNode cachedData = ctx.usePointQuery() ? CACHE.get(ctx) : CACHE.getLatestOf(ctx);

    // If cached data is not found, return a not found response
    if (Objects.isNull(cachedData)) {
      return getErrorResponse(HttpStatusCode.NOT_FOUND_404, createErrorContextForNotFound());
    }

    ctx.obtainVersionFromPayloadIfNecessary(cachedData);

    // Check if state transition is required based on TmfStatePath, and update cached data if
    // necessary
    if (needToChangeState(ctx, cachedData)) {
      ObjectNode o = ((ObjectNode) cachedData);
      o.put(ctx.getTmfStatePath().getVariableName(), ctx.getTmfStatePath().getFinalState());
      setUpdateFields((ObjectNode) cachedData);
    }

    // Update the last access time of cached data in the cache
    CACHE.touch(ctx);

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
    fieldNames.add(ID);
    fieldNames.add(HREF);
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
   * @param ctx The request context.
   * @param cachedData The cached data associated with the domain and ID.
   * @return true if a state transition is required, false otherwise.
   */
  private boolean needToChangeState(RequestContext ctx, JsonNode cachedData) {
    if (ctx.isVersioned()) {
      String latestVersion = CACHE.getLatestVersion(ctx.getDomain(), ctx.getId());
      if (!Objects.equals(ctx.getId().getVersion(), latestVersion)) {
        return false;
      }
    }
    return !(cachedData.has(UPDATED_DATE) || cachedData.has(UPDATED_BY))
        && cachedData.has(ctx.getTmfStatePath().getVariableName())
        && cachedData
            .get(ctx.getTmfStatePath().getVariableName())
            .asText()
            .equals(ctx.getTmfStatePath().getInitialState());
  }
}
