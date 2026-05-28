package org.opentmf.mockserver.callback;

import static org.opentmf.mockserver.model.TmfConstants.CREATED_BY;
import static org.opentmf.mockserver.model.TmfConstants.CREATED_DATE;
import static org.opentmf.mockserver.model.TmfConstants.HREF;
import static org.opentmf.mockserver.model.TmfConstants.ID;
import static org.opentmf.mockserver.model.TmfConstants.REVISION;
import static org.opentmf.mockserver.model.TmfConstants.VERSION;
import static org.opentmf.mockserver.util.AuditFieldUtil.setUpdateFields;
import static org.opentmf.mockserver.util.ErrorResponseUtil.getErrorResponse;

import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.model.MediaType;
import org.opentmf.mockserver.model.RequestContext;
import org.opentmf.mockserver.token.TokenEnforcer;
import org.opentmf.mockserver.util.JacksonUtil;
import org.opentmf.mockserver.util.PayloadCache;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 *
 *
 * <h2>DynamicPutCallback</h2>
 *
 * <p>Implements HTTP PUT semantics (RFC 9110 §9.3.4): the request body represents the complete
 * desired state of the resource identified by the request URI, and the operation is idempotent.
 *
 * <ul>
 *   <li>Bound to {@code PUT /{basePath}/{id}} (with optional {@code :(version=XYZ)} suffix or
 *       {@code ?version=XYZ} for versioned entities).
 *   <li>The id (and version when present) in the URI are authoritative. If the body contains a
 *       conflicting {@code id} or {@code version}, the request is rejected with 400.
 *   <li>If no cached payload matches the URI, the resource is created: {@code href}, {@code id},
 *       {@code version} (defaulting to {@code "0"} for versioned entities when not supplied), the
 *       initial state field, {@code createdBy}/{@code createdDate}/{@code revision=0}, and any
 *       {@code ADDITIONAL_FIELDS} are populated. Response is 201 Created.
 *   <li>If a cached payload exists, it is replaced wholesale by the request body. Server-managed
 *       fields are reconciled: {@code id}, {@code version}, {@code href}, {@code createdBy} and
 *       {@code createdDate} are taken from the existing entry; {@code revision} is incremented;
 *       {@code updatedBy} / {@code updatedDate} are stamped. Response is 200 OK.
 *   <li>Idempotent: replaying the same PUT always converges to the same observable resource state
 *       (modulo {@code updatedDate} / {@code updatedBy} / {@code revision}, which by design record
 *       the event).
 *   <li>Authorization: requires {@code writer} or {@code admin} role when token enforcement is on.
 * </ul>
 *
 * @author Gokhan Demir
 */
public class DynamicPutCallback implements ExpectationResponseCallback {

  private static final PayloadCache CACHE = PayloadCache.getInstance();

  @Override
  public HttpResponse handle(HttpRequest httpRequest) {
    HttpResponse authError =
        TokenEnforcer.getInstance().validateWithRoles(httpRequest, "writer", "admin");
    if (authError != null) {
      return authError;
    }

    ObjectNode parsedBody;
    try {
      JsonNode node = JacksonUtil.readAsTree(httpRequest.getBodyAsString());
      if (node == null || !node.isObject()) {
        return getErrorResponse(HttpStatusCode.BAD_REQUEST_400, "Body must be a JSON object.");
      }
      parsedBody = (ObjectNode) node;
    } catch (Exception e) {
      return getErrorResponse(
          HttpStatusCode.BAD_REQUEST_400, "Body is not valid JSON: " + e.getMessage());
    }

    RequestContext ctx = RequestContext.initialize(httpRequest, true, null);

    String urlId = ctx.getId().getId();
    if (parsedBody.has(ID) && !urlId.equals(parsedBody.get(ID).asText())) {
      return getErrorResponse(
          HttpStatusCode.BAD_REQUEST_400,
          "Body 'id' ["
              + parsedBody.get(ID).asText()
              + "] does not match URL id ["
              + urlId
              + "].");
    }
    String urlVersion = ctx.getId().getVersion();
    if (urlVersion != null
        && parsedBody.has(VERSION)
        && !urlVersion.equals(parsedBody.get(VERSION).asText())) {
      return getErrorResponse(
          HttpStatusCode.BAD_REQUEST_400,
          "Body 'version' ["
              + parsedBody.get(VERSION).asText()
              + "] does not match URL version ["
              + urlVersion
              + "].");
    }

    JsonNode existing = ctx.usePointQuery() ? CACHE.get(ctx) : CACHE.getLatestOf(ctx);
    if (existing == null) {
      return create(ctx, parsedBody);
    }
    ctx.obtainVersionFromPayloadIfNecessary(existing);
    return replace(ctx, parsedBody, existing);
  }

  private HttpResponse create(RequestContext ctx, ObjectNode parsedBody) {
    ctx.generateNewIdIfNecessary();
    DynamicPostCallback.prepareForCacheWithHref(ctx, parsedBody, buildHref(ctx));
    CACHE.put(ctx, parsedBody);
    return HttpResponse.response()
        .withStatusCode(HttpStatusCode.CREATED_201.code())
        .withContentType(MediaType.APPLICATION_JSON)
        .withBody(JacksonUtil.writeAsString(parsedBody));
  }

  private HttpResponse replace(RequestContext ctx, ObjectNode parsedBody, JsonNode existing) {
    parsedBody.put(ID, ctx.getId().getId());
    if (ctx.isVersioned()) {
      parsedBody.put(VERSION, ctx.getId().getVersion());
    }
    if (existing.has(HREF)) {
      parsedBody.set(HREF, existing.get(HREF));
    } else {
      parsedBody.put(HREF, buildHref(ctx));
    }
    if (existing.has(CREATED_BY)) {
      parsedBody.set(CREATED_BY, existing.get(CREATED_BY));
    }
    if (existing.has(CREATED_DATE)) {
      parsedBody.set(CREATED_DATE, existing.get(CREATED_DATE));
    }
    if (!parsedBody.has(REVISION) && existing.has(REVISION)) {
      parsedBody.set(REVISION, existing.get(REVISION));
    }
    setUpdateFields(parsedBody);
    CACHE.update(ctx, parsedBody);
    return HttpResponse.response()
        .withStatusCode(HttpStatusCode.OK_200.code())
        .withContentType(MediaType.APPLICATION_JSON)
        .withBody(JacksonUtil.writeAsString(parsedBody));
  }

  private static String buildHref(RequestContext ctx) {
    return "/" + ctx.getDomain() + "/" + ctx.getId().getHrefSuffix();
  }
}
