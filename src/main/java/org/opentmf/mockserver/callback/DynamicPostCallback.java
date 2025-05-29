package org.opentmf.mockserver.callback;

import static org.opentmf.mockserver.model.TmfConstants.HREF;
import static org.opentmf.mockserver.model.TmfConstants.ID;
import static org.opentmf.mockserver.model.TmfConstants.UPDATED_BY;
import static org.opentmf.mockserver.model.TmfConstants.UPDATED_DATE;
import static org.opentmf.mockserver.model.TmfConstants.VERSION;
import static org.opentmf.mockserver.util.AuditFieldUtil.setCreateFields;
import static org.opentmf.mockserver.util.Constants.ADDITIONAL_FIELDS;
import static org.opentmf.mockserver.util.ErrorResponseUtil.getErrorResponse;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.RandomStringUtils;
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
 * <h2>DynamicPostCallback</h2>
 *
 * <ul>
 *   <li>Tries to retrieve <code>id</code> and <code>version</code> (if versioned entity) from the payload.
 *   <li>If versioned entity but no <code>version</code> in the payload, tries to retrieve the version from the
 *       path using <code>:(version=XYZ)</code>
 *   <li>If versioned entity but no <code>version</code> found yet, tries to get the version from the query
 *       parameters like <code>?version=XYZ</code>
 *   <li>If <code>id</code> and `<code>version</code> (if versioned entity) is provided, checks if that exists in the
 *       payload cache. Returns 400 if so.
 *   <li>Uses either the provided id, or generates a new id for the posted payload.
 *   <li>If a versioned entity and version is not provided, sets <code>"version": "0"</code>.
 *   <li>Adds createdBy, createdDate and revision fields. Overrides if they are already provided.
 *   <li>Removes updatedDate and updatedBy, if they are provided in the payload.
 *   <li>Decides the state field name and initial value according to the path.
 *   <li>If state (or status) is not provided, sets the state value to the default initial. Here is
 *       the state value matrix that matches the configured path according to the type field: <br>
 *       <table style="border:1px solid #666; padding:4px">
 *  <thead>
 *  <tr>
 *  <th>Type</th>
 *  <th>Field name</th>
 *  <th>Initial Value</th>
 *  <th>Final Value</th>
 *  </tr>
 *  </thead>
 *  <tbody>
 *  <tr>
 *  <td>Orders</td>
 *  <td>state</td>
 *  <td>acknowledged</td>
 *  <td>completed</td>
 *  </tr>
 *  <tr>
 *  <td>Inventory</td>
 *  <td>status</td>
 *  <td>created</td>
 *  <td>active</td>
 *  </tr>
 *  <tr>
 *  <td>Catalog</td>
 *  <td>lifecycleStatus</td>
 *  <td>inStudy</td>
 *  <td>inDesign</td>
 *  </tr>
 *  <tr>
 *  <td>Candidate</td>
 *  <td>lifecycleStatus</td>
 *  <td>inStudy</td>
 *  <td>inDesign</td>
 *  </tr>
 *  <tr>
 *  <td>Default</td>
 *  <td>state</td>
 *  <td>acknowledged</td>
 *  <td>completed</td>
 *  </tr>
 *  </tbody>
 *  </table>
 *       <br>
 *   <li>If at least two of the state, status and/or lifecycleStatus are provided at the same time,
 *       returns 400.
 *   <li>If environment variable ADDITIONAL_FIELDS is provided, splits it using comma, and for each
 *       item, if the item is provided as `key=value`, sets to the resulting payload `"key":
 *       "value"`. If the item is provided without an equals sign, sets to the resulting payload
 *       `"item": "${randomAlphanumeric_10_characters}"
 *   <li>Caches the payload, and returns 200.
 * </ul>
 *
 * @author Gokhan Demir
 */
public class DynamicPostCallback implements ExpectationResponseCallback {

  private static final PayloadCache CACHE = PayloadCache.getInstance();

  @Override
  public HttpResponse handle(HttpRequest httpRequest) {
    // Parse the request body
    String body = httpRequest.getBodyAsString();
    ObjectNode parsedBody = (ObjectNode) JacksonUtil.readAsTree(body);

    RequestContext ctx = RequestContext.initialize(httpRequest, false, parsedBody);

    // Check if the payload contains an ID that already exists in the cache
    if (ctx.hasId() && CACHE.get(ctx) != null) {
      return getErrorResponse(
          HttpStatusCode.BAD_REQUEST_400, "[" + ctx.getId() + "] already exists.");
    }

    // Generate a new ID if not already present in the payload
    ctx.generateNewIdIfNecessary();
    parsedBody.put(ID, ctx.getId().getId());
    if (ctx.isVersioned()) {
      parsedBody.put(VERSION, ctx.getId().getVersion());
    }
    parsedBody.put(HREF, ctx.toHref());

    // Set the initial state if not already present in the payload
    if (!parsedBody.has(ctx.getTmfStatePath().getVariableName())) {
      parsedBody.put(
          ctx.getTmfStatePath().getVariableName(), ctx.getTmfStatePath().getInitialState());
    }

    // Remove 'updatedBy' and 'updatedDate' fields from the payload
    removeUpdateFieldIfExist(parsedBody);

    // Set create fields using AuditFieldUtil
    setCreateFields(parsedBody);

    // add additional fields if configured through the system property: ADDITIONAL_FIELDS
    addAdditionalFields(parsedBody);

    // Generate response JSON
    String responseJson = JacksonUtil.writeAsString(parsedBody);

    CACHE.put(ctx, parsedBody);
    return HttpResponse.response()
        .withStatusCode(HttpStatusCode.OK_200.code())
        .withContentType(MediaType.APPLICATION_JSON)
        .withBody(responseJson);
  }

  private void removeUpdateFieldIfExist(ObjectNode objectNode) {
    objectNode.remove(UPDATED_BY);
    objectNode.remove(UPDATED_DATE);
  }

  private void addAdditionalFields(ObjectNode node) {
    String additionalFields = System.getenv(ADDITIONAL_FIELDS);
    if (additionalFields == null) {
      return;
    }
    String[] fields = additionalFields.split(",");
    for (String field : fields) {
      if (field.trim().isEmpty()) {
        continue;
      }
      String[] fieldParts = field.split("=");
      if (fieldParts.length == 2) {
        node.put(fieldParts[0].trim(), fieldParts[1].trim());
      } else {
        node.put(field.trim(), RandomStringUtils.randomAlphanumeric(10));
      }
    }
  }
}
