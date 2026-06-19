package org.opentmf.mockserver.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentmf.mockserver.util.Constants.ADDITIONAL_FIELDS;
import static org.opentmf.mockserver.util.Constants.CACHE_DURATION_MILLIS;
import static org.opentmf.mockserver.util.Constants.THREE_SECONDS;
import static org.opentmf.mockserver.util.IdempotencyGuard.IDEMPOTENCY_KEY_HEADER;
import static org.opentmf.mockserver.util.IdempotencyGuard.REPLAY_HEADER;

import io.hypersistence.tsid.TSID;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.TreeMap;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.opentmf.mockserver.callback.DynamicDeleteCallback;
import org.opentmf.mockserver.callback.DynamicMergePatchCallback;
import org.opentmf.mockserver.callback.DynamicPostCallback;
import org.opentmf.mockserver.callback.DynamicPutCallback;
import org.opentmf.mockserver.model.Id;
import tools.jackson.databind.JsonNode;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith(SystemStubsExtension.class)
class IdempotencyGuardTests {

  private static final PayloadCache CACHE = PayloadCache.getInstance();
  private static final IdempotencyCache IDEMPOTENCY = IdempotencyCache.getInstance();

  private final DynamicPostCallback postCallback = new DynamicPostCallback();
  private final DynamicPutCallback putCallback = new DynamicPutCallback();
  private final DynamicMergePatchCallback mergePatchCallback = new DynamicMergePatchCallback();
  private final DynamicDeleteCallback deleteCallback = new DynamicDeleteCallback();

  @SystemStub
  private static final EnvironmentVariables TEST_ENV_VARIABLES =
      new EnvironmentVariables(CACHE_DURATION_MILLIS, THREE_SECONDS, ADDITIONAL_FIELDS, "project");

  @Test
  void post_withoutKey_doesNotRecordReplay() {
    String domain = randomDomain();
    HttpResponse first = postCallback.handle(postRequest(domain, "{\"description\":\"x\"}", null));
    HttpResponse second = postCallback.handle(postRequest(domain, "{\"description\":\"y\"}", null));

    assertEquals(201, first.getStatusCode());
    assertEquals(201, second.getStatusCode());
    String firstId = JacksonUtil.readAsTree(first.getBodyAsString()).get("id").asText();
    String secondId = JacksonUtil.readAsTree(second.getBodyAsString()).get("id").asText();
    assertNotEquals(firstId, secondId);
    assertFalse(first.containsHeader(REPLAY_HEADER), "no key → no replay header");
  }

  @Test
  void post_replay_returnsSameBodyAndReplayHeader() {
    String domain = randomDomain();
    String key = randomKey();
    HttpResponse first =
        postCallback.handle(postRequest(domain, "{\"description\":\"once\"}", key));
    HttpResponse replay =
        postCallback.handle(postRequest(domain, "{\"description\":\"once\"}", key));

    assertEquals(201, first.getStatusCode());
    assertEquals(201, replay.getStatusCode());
    assertEquals(first.getBodyAsString(), replay.getBodyAsString());
    assertEquals("true", replay.getFirstHeader(REPLAY_HEADER));
    assertFalse(first.containsHeader(REPLAY_HEADER), "original response carries no replay marker");
  }

  @Test
  void post_replay_returnsSameGeneratedId() {
    String domain = randomDomain();
    String key = randomKey();
    HttpResponse first = postCallback.handle(postRequest(domain, "{}", key));
    HttpResponse replay = postCallback.handle(postRequest(domain, "{}", key));

    String firstId = JacksonUtil.readAsTree(first.getBodyAsString()).get("id").asText();
    String replayId = JacksonUtil.readAsTree(replay.getBodyAsString()).get("id").asText();
    assertEquals(firstId, replayId);
  }

  @Test
  void post_replay_touchesUnderlyingPayloadTtl() {
    String domain = randomDomain();
    String key = randomKey();
    HttpResponse first =
        postCallback.handle(postRequest(domain, "{\"description\":\"touch\"}", key));
    String resourceId = JacksonUtil.readAsTree(first.getBodyAsString()).get("id").asText();
    Id id = new Id();
    id.setId(resourceId);

    long beforeReplay = peekTimestamp(domain, id);
    sleepBriefly();
    postCallback.handle(postRequest(domain, "{\"description\":\"touch\"}", key));
    long afterReplay = peekTimestamp(domain, id);

    assertTrue(
        afterReplay > beforeReplay,
        "Replay should advance the cache timestamp from " + beforeReplay + " to " + afterReplay);
  }

  @Test
  void put_replay_returnsSameBody() {
    String domain = randomDomain();
    String resourceId = TSID.Factory.getTsid().toString();
    String key = randomKey();
    HttpResponse first =
        putCallback.handle(
            putRequest("/" + domain + "/" + resourceId, "{\"description\":\"v1\"}", key));
    HttpResponse replay =
        putCallback.handle(
            putRequest("/" + domain + "/" + resourceId, "{\"description\":\"DIFFERENT\"}", key));

    assertEquals(201, first.getStatusCode());
    assertEquals(201, replay.getStatusCode());
    assertEquals(first.getBodyAsString(), replay.getBodyAsString(),
        "replay returns ORIGINAL body, ignoring the new request body");
    assertEquals("true", replay.getFirstHeader(REPLAY_HEADER));
  }

  @Test
  void mergePatch_replay_returnsSameUpdatedBody() {
    String domain = randomDomain();
    String resourceId = TSID.Factory.getTsid().toString();
    postCallback.handle(
        postRequest(domain, "{\"id\":\"" + resourceId + "\",\"description\":\"original\"}", null));

    String key = randomKey();
    HttpResponse first =
        mergePatchCallback.handle(
            patchRequest(
                "/" + domain + "/" + resourceId, "{\"description\":\"patched\"}", key));
    HttpResponse replay =
        mergePatchCallback.handle(
            patchRequest(
                "/" + domain + "/" + resourceId, "{\"description\":\"patched\"}", key));

    assertEquals(200, first.getStatusCode());
    assertEquals(200, replay.getStatusCode());
    assertEquals(first.getBodyAsString(), replay.getBodyAsString());
    assertEquals("true", replay.getFirstHeader(REPLAY_HEADER));
  }

  @Test
  void delete_replay_returns204AfterResourceGone() {
    String domain = randomDomain();
    String resourceId = TSID.Factory.getTsid().toString();
    postCallback.handle(
        postRequest(domain, "{\"id\":\"" + resourceId + "\",\"description\":\"to delete\"}", null));

    String key = randomKey();
    HttpResponse first =
        deleteCallback.handle(deleteRequest("/" + domain + "/" + resourceId, key));
    HttpResponse replay =
        deleteCallback.handle(deleteRequest("/" + domain + "/" + resourceId, key));

    assertEquals(204, first.getStatusCode());
    assertEquals(204, replay.getStatusCode());
    assertEquals("true", replay.getFirstHeader(REPLAY_HEADER));
  }

  @Test
  void delete_replay_doesNotRequireResourceToExist() {
    String domain = randomDomain();
    String resourceId = TSID.Factory.getTsid().toString();
    postCallback.handle(
        postRequest(domain, "{\"id\":\"" + resourceId + "\",\"description\":\"ephemeral\"}", null));

    String key = randomKey();
    deleteCallback.handle(deleteRequest("/" + domain + "/" + resourceId, key));
    // Replay without the underlying resource: still replays 204 (not 404)
    HttpResponse replay =
        deleteCallback.handle(deleteRequest("/" + domain + "/" + resourceId, key));
    assertEquals(204, replay.getStatusCode());
  }

  @Test
  void replay_keyReusedOnDifferentPath_returns422() {
    String domain = randomDomain();
    String key = randomKey();
    HttpResponse first =
        postCallback.handle(postRequest(domain, "{\"description\":\"a\"}", key));
    assertEquals(201, first.getStatusCode());

    String otherDomain = randomDomain();
    HttpResponse conflict =
        postCallback.handle(postRequest(otherDomain, "{\"description\":\"b\"}", key));

    assertEquals(422, conflict.getStatusCode());
    assertTrue(conflict.getBodyAsString().contains("Idempotency-Key was previously used"));
  }

  @Test
  void replay_keyReusedOnDifferentMethod_returns422() {
    String domain = randomDomain();
    String resourceId = TSID.Factory.getTsid().toString();
    postCallback.handle(
        postRequest(domain, "{\"id\":\"" + resourceId + "\",\"description\":\"x\"}", null));

    String key = randomKey();
    HttpResponse putResp =
        putCallback.handle(
            putRequest("/" + domain + "/" + resourceId, "{\"description\":\"y\"}", key));
    assertEquals(200, putResp.getStatusCode());

    HttpResponse delResp =
        deleteCallback.handle(deleteRequest("/" + domain + "/" + resourceId, key));
    assertEquals(422, delResp.getStatusCode());
  }

  @Test
  void precheck_blankKeyHeader_isTreatedAsNoKey() {
    String domain = randomDomain();
    HttpResponse first = postCallback.handle(postRequest(domain, "{\"description\":\"x\"}", "   "));
    HttpResponse second =
        postCallback.handle(postRequest(domain, "{\"description\":\"y\"}", "   "));

    assertEquals(201, first.getStatusCode());
    assertEquals(201, second.getStatusCode());
    assertFalse(first.containsHeader(REPLAY_HEADER));
    assertFalse(second.containsHeader(REPLAY_HEADER));
  }

  @Test
  void precheck_oversizedKey_returns400() {
    String domain = randomDomain();
    String tooLong = "x".repeat(300);
    HttpResponse resp =
        postCallback.handle(postRequest(domain, "{\"description\":\"a\"}", tooLong));
    assertEquals(400, resp.getStatusCode());
    assertTrue(resp.getBodyAsString().contains("exceeds"));
  }

  @Test
  void evictForResource_dropsRecordForThatResource() {
    String domain = randomDomain();
    String key = randomKey();
    HttpResponse first =
        postCallback.handle(postRequest(domain, "{\"description\":\"alive\"}", key));
    String resourceId = JacksonUtil.readAsTree(first.getBodyAsString()).get("id").asText();
    Id id = new Id();
    id.setId(resourceId);
    assertNotNull(IDEMPOTENCY.get(key));

    IDEMPOTENCY.evictForResource(domain, id);

    assertNull(IDEMPOTENCY.get(key), "record dropped after eviction hook fired");
    HttpResponse afterEviction =
        postCallback.handle(postRequest(domain, "{\"description\":\"again\"}", key));
    assertEquals(201, afterEviction.getStatusCode());
    JsonNode body = JacksonUtil.readAsTree(afterEviction.getBodyAsString());
    assertNotEquals(resourceId, body.get("id").asText(),
        "after eviction the same key behaves as a fresh request");
  }

  @Test
  void touchByResource_movesTimestampForward() {
    String domain = randomDomain();
    String resourceId = TSID.Factory.getTsid().toString();
    postCallback.handle(
        postRequest(domain, "{\"id\":\"" + resourceId + "\",\"description\":\"t\"}", null));
    Id id = new Id();
    id.setId(resourceId);

    long before = peekTimestamp(domain, id);
    sleepBriefly();
    CACHE.touchByResource(domain, id);
    long after = peekTimestamp(domain, id);

    assertTrue(after > before, "touchByResource should advance the entry's timestamp");
  }

  @Test
  void touchByResource_isNoOpForMissingDomain() {
    Id id = new Id();
    id.setId("nope");
    CACHE.touchByResource("does-not-exist-" + RandomStringUtils.randomAlphabetic(6), id);
    CACHE.touchByResource(null, id);
    CACHE.touchByResource("anything", null);
  }

  private static HttpRequest postRequest(String domain, String body, String key) {
    return withKey(
        new HttpRequest().withMethod("POST").withPath("/" + domain).withBody(body), key);
  }

  private static HttpRequest putRequest(String path, String body, String key) {
    return withKey(new HttpRequest().withMethod("PUT").withPath(path).withBody(body), key);
  }

  private static HttpRequest patchRequest(String path, String body, String key) {
    return withKey(new HttpRequest().withMethod("PATCH").withPath(path).withBody(body), key);
  }

  private static HttpRequest deleteRequest(String path, String key) {
    return withKey(new HttpRequest().withMethod("DELETE").withPath(path), key);
  }

  private static HttpRequest withKey(HttpRequest req, String key) {
    if (key != null) {
      req.withHeader(IDEMPOTENCY_KEY_HEADER, key);
    }
    return req;
  }

  private static String randomDomain() {
    return RandomStringUtils.randomAlphabetic(8).toLowerCase();
  }

  private static String randomKey() {
    return "key-" + RandomStringUtils.randomAlphanumeric(16);
  }

  private static void sleepBriefly() {
    try {
      Thread.sleep(5);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @SuppressWarnings("unchecked")
  private static long peekTimestamp(String domain, Id id) {
    try {
      Field f = PayloadCache.class.getDeclaredField("timeCache");
      f.setAccessible(true);
      Map<String, TreeMap<Id, Long>> map = (Map<String, TreeMap<Id, Long>>) f.get(CACHE);
      TreeMap<Id, Long> domainMap = map.get(domain);
      assertNotNull(domainMap, "no time-cache entry for domain " + domain);
      Long ts = domainMap.get(id);
      assertNotNull(ts, "no timestamp for id " + id);
      return ts;
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
