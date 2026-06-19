package org.opentmf.mockserver.callback;

import static org.opentmf.mockserver.model.TmfConstants.HREF;
import static org.opentmf.mockserver.model.TmfConstants.ID;
import static org.opentmf.mockserver.util.ErrorResponseUtil.getErrorResponse;
import static org.opentmf.mockserver.util.HttpRequestUtil.extractFields;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.model.MediaType;
import org.opentmf.mockserver.model.Id;
import org.opentmf.mockserver.model.RequestContext;
import org.opentmf.mockserver.token.TokenEnforcer;
import org.opentmf.mockserver.util.IdempotencyGuard;
import org.opentmf.mockserver.util.JacksonUtil;
import org.opentmf.mockserver.util.PayloadCache;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 *
 *
 * <h2>DynamicJsonPatchCollectionCallback</h2>
 *
 * <p>Implements TMF630 REST API Design Guidelines Part 1 §6.2 "Creating Multiple Resources".
 *
 * <ul>
 *   <li>Bound to {@code PATCH /{basePath}} (the collection URL, no id segment) with
 *       {@code Content-Type: application/json-patch+json}.
 *   <li>Body MUST be a non-empty JSON array of operations. Strict §6.2: every operation MUST be
 *       {@code {"op":"add", "path":"/", "value":{...}}}. Other ops or paths return 400.
 *   <li>Each {@code value} is processed exactly like a single POST: id is generated when missing,
 *       {@code href}, initial state, {@code createdBy}/{@code createdDate}/{@code revision} and
 *       {@code ADDITIONAL_FIELDS} are populated.
 *   <li>Atomic per RFC 5789: if any item fails validation, no resources are created. Duplicate ids
 *       (within the batch or against the cache) return 409 Conflict.
 *   <li>Response is 200 with a JSON array of full resource representations. When
 *       {@code ?fields=none} is provided, each item is projected to {@code id} and {@code href}
 *       only; other {@code fields=} lists project to the named attributes plus {@code id}/{@code
 *       href}.
 * </ul>
 *
 * @author Gokhan Demir
 */
public class DynamicJsonPatchCollectionCallback implements ExpectationResponseCallback {

  private static final PayloadCache CACHE = PayloadCache.getInstance();

  @Override
  public HttpResponse handle(HttpRequest httpRequest) {
    HttpResponse authError =
        TokenEnforcer.getInstance().validateWithRoles(httpRequest, "writer", "admin");
    if (authError != null) {
      return authError;
    }

    HttpResponse replay = IdempotencyGuard.precheck(httpRequest);
    if (replay != null) {
      return replay;
    }

    JsonNode body;
    try {
      body = JacksonUtil.readAsTree(httpRequest.getBodyAsString());
    } catch (Exception e) {
      return getErrorResponse(
          HttpStatusCode.BAD_REQUEST_400, "Body is not valid JSON: " + e.getMessage());
    }
    if (body == null || !body.isArray()) {
      return getErrorResponse(
          HttpStatusCode.BAD_REQUEST_400, "Body must be a JSON array of patch operations.");
    }
    if (body.isEmpty()) {
      return getErrorResponse(
          HttpStatusCode.BAD_REQUEST_400, "Body must contain at least one patch operation.");
    }

    List<PreparedItem> plan = new ArrayList<>(body.size());
    for (int i = 0; i < body.size(); i++) {
      JsonNode op = body.get(i);
      HttpResponse opError = validateOp(op, i);
      if (opError != null) {
        return opError;
      }
      ObjectNode value = (ObjectNode) op.get("value").deepCopy();
      RequestContext ctx = RequestContext.initialize(httpRequest, false, value);
      DynamicPostCallback.prepareForCache(ctx, value);
      plan.add(new PreparedItem(ctx, value));
    }

    Set<Id> seen = new HashSet<>();
    for (PreparedItem item : plan) {
      Id id = item.ctx.getId();
      if (!seen.add(id)) {
        return getErrorResponse(
            HttpStatusCode.CONFLICT_409, "Duplicate id within batch: [" + id + "]");
      }
      if (CACHE.get(item.ctx) != null) {
        return getErrorResponse(HttpStatusCode.CONFLICT_409, "[" + id + "] already exists.");
      }
    }

    for (PreparedItem item : plan) {
      CACHE.put(item.ctx, item.body);
    }

    Set<String> fields = extractFields(httpRequest);
    ArrayNode result = JacksonUtil.createArrayNode();
    for (PreparedItem item : plan) {
      result.add(filterFields(item.body, fields));
    }
    HttpResponse response =
        HttpResponse.response()
            .withStatusCode(HttpStatusCode.OK_200.code())
            .withContentType(MediaType.APPLICATION_JSON)
            .withBody(JacksonUtil.writeAsString(result));
    IdempotencyGuard.record(httpRequest, response, null);
    return response;
  }

  private HttpResponse validateOp(JsonNode op, int index) {
    if (op == null || !op.isObject()) {
      return getErrorResponse(
          HttpStatusCode.BAD_REQUEST_400,
          "Op " + index + ": each operation must be a JSON object.");
    }
    String opName = op.has("op") ? op.get("op").asText() : null;
    if (!"add".equals(opName)) {
      return getErrorResponse(
          HttpStatusCode.BAD_REQUEST_400,
          "Op " + index + ": only 'add' is supported, got '" + opName + "'.");
    }
    String path = op.has("path") ? op.get("path").asText() : null;
    if (!"/".equals(path)) {
      return getErrorResponse(
          HttpStatusCode.BAD_REQUEST_400, "Op " + index + ": path must be '/', got '" + path + "'.");
    }
    if (!op.has("value") || !op.get("value").isObject()) {
      return getErrorResponse(
          HttpStatusCode.BAD_REQUEST_400, "Op " + index + ": 'value' must be a JSON object.");
    }
    return null;
  }

  private JsonNode filterFields(ObjectNode src, Set<String> fields) {
    if (fields.isEmpty()) {
      return src;
    }
    fields.add(ID);
    fields.add(HREF);
    ObjectNode out = JacksonUtil.createObjectNode();
    for (String f : fields) {
      if (src.has(f)) {
        out.set(f, src.get(f));
      }
    }
    return out;
  }

  private record PreparedItem(RequestContext ctx, ObjectNode body) {}
}
