package org.opentmf.mockserver.callback;

import static org.opentmf.mockserver.model.Error.createErrorContextForNotFound;
import static org.opentmf.mockserver.util.ErrorResponseUtil.getErrorResponse;

import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;
import org.opentmf.mockserver.model.RequestContext;
import org.opentmf.mockserver.token.TokenEnforcer;
import org.opentmf.mockserver.util.IdempotencyGuard;
import org.opentmf.mockserver.util.PayloadCache;
import tools.jackson.databind.JsonNode;

/**
 * Shared preamble for callbacks that operate on an already-cached entry (DELETE, JSON Patch,
 * Merge Patch). Each of those needs the same sequence: auth check, idempotency precheck, request
 * context, cache lookup, {@code 404} short-circuit, and version resolution from payload. Rather
 * than duplicate that in every callback, this loader runs it once and returns either the response
 * to short-circuit with, or the loaded {@link RequestContext} + cached {@link JsonNode}.
 */
public final class ExistingEntryLoader {

  private static final PayloadCache CACHE = PayloadCache.getInstance();

  private ExistingEntryLoader() {}

  public static Result load(HttpRequest httpRequest) {
    HttpResponse authError = TokenEnforcer.getInstance().validateForRequest(httpRequest);
    if (authError != null) {
      return Result.shortCircuit(authError);
    }
    HttpResponse replay = IdempotencyGuard.precheck(httpRequest);
    if (replay != null) {
      return Result.shortCircuit(replay);
    }
    RequestContext ctx = RequestContext.initialize(httpRequest, true, null);
    JsonNode cachedData = ctx.usePointQuery() ? CACHE.get(ctx) : CACHE.getLatestOf(ctx);
    if (cachedData == null) {
      return Result.shortCircuit(
          getErrorResponse(HttpStatusCode.NOT_FOUND_404, createErrorContextForNotFound()));
    }
    ctx.obtainVersionFromPayloadIfNecessary(cachedData);
    return Result.loaded(ctx, cachedData);
  }

  public record Result(HttpResponse shortCircuit, RequestContext ctx, JsonNode cachedData) {

    public static Result shortCircuit(HttpResponse response) {
      return new Result(response, null, null);
    }

    public static Result loaded(RequestContext ctx, JsonNode cachedData) {
      return new Result(null, ctx, cachedData);
    }

    public boolean isShortCircuit() {
      return shortCircuit != null;
    }
  }
}
