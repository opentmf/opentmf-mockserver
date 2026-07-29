package org.opentmf.mockserver.util;

import static org.opentmf.mockserver.util.ErrorResponseUtil.getErrorResponse;
import static org.opentmf.mockserver.util.JacksonUtil.writeAsString;

import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.model.MediaType;
import org.opentmf.mockserver.model.Error;
import org.opentmf.mockserver.model.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared {@code Idempotency-Key} entry/exit hooks for the mutating callbacks (POST, PUT, PATCH,
 * DELETE).
 *
 * <p>Recognises the {@code Idempotency-Key} request header (draft-ietf-httpapi-idempotency-key
 * shape, Stripe-style). On entry, {@link #precheck} returns a replay response (verbatim status +
 * body, plus an {@code X-Idempotent-Replay: true} marker) when the same key was already used for an
 * identical {@code (method, path)} 2xx mutation; the underlying resource's TTL is touched in the
 * same step. A same key reused on a different {@code (method, path)} returns {@code 422
 * Unprocessable Entity}. Missing/blank keys make every hook a no-op so non-idempotency-aware
 * clients are unaffected.
 *
 * <p>On exit, {@link #record} stashes successful (2xx) responses keyed by the request's
 * Idempotency-Key so the next retry can be replayed.
 *
 * @author Gokhan Demir
 */
public final class IdempotencyGuard {

  private static final Logger LOG = LoggerFactory.getLogger(IdempotencyGuard.class);

  public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
  public static final String REPLAY_HEADER = "X-Idempotent-Replay";
  private static final int MAX_KEY_LENGTH = 255;
  private static final int UNPROCESSABLE_ENTITY_422 = 422;

  private static final IdempotencyCache CACHE = IdempotencyCache.getInstance();
  private static final PayloadCache PAYLOAD_CACHE = PayloadCache.getInstance();

  private IdempotencyGuard() {}

  /**
   * Returns a fully-formed {@link HttpResponse} when the request should bypass normal handling — a
   * replay of an already-completed mutation, or a 422 for a conflicting key reuse — and {@code
   * null} when the caller should proceed with its usual flow. Missing/blank {@code Idempotency-Key}
   * always returns {@code null}.
   */
  public static HttpResponse precheck(HttpRequest request) {
    String key = readKey(request);
    if (key == null) {
      return null;
    }
    if (key.length() > MAX_KEY_LENGTH) {
      return getErrorResponse(
          HttpStatusCode.BAD_REQUEST_400,
          "Idempotency-Key length exceeds " + MAX_KEY_LENGTH + " characters.");
    }
    IdempotencyCache.Record record = CACHE.get(key);
    if (record == null) {
      return null;
    }
    String method = methodOf(request);
    String path = pathOf(request);
    if (!method.equals(record.getMethod()) || !path.equals(record.getPath())) {
      return unprocessableEntity(
          "Idempotency-Key was previously used on "
              + record.getMethod()
              + " "
              + record.getPath()
              + "; cannot be reused on "
              + method
              + " "
              + path
              + ".");
    }
    PAYLOAD_CACHE.touchByResource(record.getDomain(), record.getResourceId());
    CACHE.put(
        key,
        new IdempotencyCache.Record(
            record.getMethod(),
            record.getPath(),
            record.getStatusCode(),
            record.getContentType(),
            record.getBody(),
            record.getDomain(),
            record.getResourceId(),
            System.currentTimeMillis()));
    LOG.info("Idempotent replay for key=\"{}\" {} {}", key, method, path);
    return rebuild(record);
  }

  /**
   * Stashes a successful (2xx) mutation response under the request's Idempotency-Key so the next
   * retry can be replayed. No-op when the key is absent/blank or the response is non-2xx. {@code
   * ctx} provides the (domain, resourceId) used to eagerly evict the record if the underlying
   * resource is later evicted by TTL.
   */
  public static void record(HttpRequest request, HttpResponse response, RequestContext ctx) {
    String key = readKey(request);
    if (key == null) {
      return;
    }
    int status = response.getStatusCode() == null ? 0 : response.getStatusCode();
    if (status < 200 || status >= 300) {
      return;
    }
    String body = response.getBodyAsString();
    String contentType = response.getFirstHeader("Content-Type");
    String domain = ctx == null ? null : ctx.getDomain();
    CACHE.put(
        key,
        new IdempotencyCache.Record(
            methodOf(request),
            pathOf(request),
            status,
            contentType,
            body,
            domain,
            ctx == null ? null : ctx.getId(),
            System.currentTimeMillis()));
  }

  private static HttpResponse rebuild(IdempotencyCache.Record record) {
    HttpResponse response =
        HttpResponse.response()
            .withStatusCode(record.getStatusCode())
            .withHeader(REPLAY_HEADER, "true");
    if (record.getBody() != null && !record.getBody().isEmpty()) {
      response.withBody(record.getBody());
      if (record.getContentType() != null && !record.getContentType().isEmpty()) {
        response.withHeader("Content-Type", record.getContentType());
      } else {
        response.withContentType(MediaType.APPLICATION_JSON);
      }
    }
    return response;
  }

  private static String readKey(HttpRequest request) {
    String key = request.getFirstHeader(IDEMPOTENCY_KEY_HEADER);
    if (key == null) {
      return null;
    }
    key = key.trim();
    return key.isEmpty() ? null : key;
  }

  private static String methodOf(HttpRequest request) {
    return request.getMethod() == null ? "" : request.getMethod().getValue();
  }

  private static String pathOf(HttpRequest request) {
    return request.getPath() == null ? "" : request.getPath().getValue();
  }

  /**
   * 422 Unprocessable Entity is not in MockServer's {@link HttpStatusCode} enum, so render the
   * shared {@link Error} body manually.
   */
  private static HttpResponse unprocessableEntity(String message) {
    Error error = new Error(message, UNPROCESSABLE_ENTITY_422, "Unprocessable Entity");
    return HttpResponse.response()
        .withStatusCode(UNPROCESSABLE_ENTITY_422)
        .withContentType(MediaType.APPLICATION_JSON)
        .withBody(writeAsString(error));
  }
}
