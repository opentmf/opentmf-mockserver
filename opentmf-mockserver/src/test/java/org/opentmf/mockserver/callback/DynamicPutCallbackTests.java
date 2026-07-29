package org.opentmf.mockserver.callback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentmf.mockserver.model.TmfStatePath.CATALOG;
import static org.opentmf.mockserver.util.Constants.ADDITIONAL_FIELDS;
import static org.opentmf.mockserver.util.Constants.CACHE_DURATION_MILLIS;
import static org.opentmf.mockserver.util.Constants.THREE_SECONDS;

import io.hypersistence.tsid.TSID;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.opentmf.mockserver.model.RequestContext;
import org.opentmf.mockserver.util.JacksonUtil;
import org.opentmf.mockserver.util.PayloadCache;
import tools.jackson.databind.JsonNode;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith(SystemStubsExtension.class)
class DynamicPutCallbackTests {

  private static final PayloadCache CACHE = PayloadCache.getInstance();
  private final DynamicPutCallback callback = new DynamicPutCallback();

  @SystemStub
  private static final EnvironmentVariables TEST_ENV_VARIABLES =
      new EnvironmentVariables(CACHE_DURATION_MILLIS, THREE_SECONDS, ADDITIONAL_FIELDS, "project");

  @Test
  void put_whenNotInCache_createsAndReturns201() {
    String domain = RandomStringUtils.insecure().nextAlphabetic(5);
    String id = TSID.Factory.getTsid().toString();

    HttpResponse resp =
        callback.handle(
            putRequest("/" + domain + "/" + id, "{\"description\":\"hello\"}"));

    assertEquals(201, resp.getStatusCode());
    JsonNode body = JacksonUtil.readAsTree(resp.getBodyAsString());
    assertEquals(id, body.get("id").asString());
    assertEquals("/" + domain + "/" + id, body.get("href").asString());
    assertEquals("hello", body.get("description").asString());
    assertNotNull(body.get("createdBy"));
    assertNotNull(body.get("createdDate"));
    assertEquals(0L, body.get("revision").asLong());
    assertNotNull(body.get("project"));
    assertEquals("acknowledged", body.get("state").asString());
    assertNull(body.get("updatedBy"));
    assertNull(body.get("updatedDate"));
    assertNotNull(CACHE.get(ctxFor(domain, id, null)));
  }

  @Test
  void put_whenInCache_replacesAndReturns200() {
    String domain = RandomStringUtils.insecure().nextAlphabetic(5);
    String id = TSID.Factory.getTsid().toString();
    JsonNode created =
        JacksonUtil.readAsTree(
            callback
                .handle(putRequest("/" + domain + "/" + id, "{\"description\":\"v1\"}"))
                .getBodyAsString());
    String createdBy = created.get("createdBy").asString();
    String createdDate = created.get("createdDate").asString();

    HttpResponse resp =
        callback.handle(
            putRequest(
                "/" + domain + "/" + id,
                "{\"description\":\"v2\",\"extra\":\"new\",\"state\":\"completed\"}"));

    assertEquals(200, resp.getStatusCode());
    JsonNode body = JacksonUtil.readAsTree(resp.getBodyAsString());
    assertEquals(id, body.get("id").asString());
    assertEquals("/" + domain + "/" + id, body.get("href").asString());
    assertEquals("v2", body.get("description").asString());
    assertEquals("new", body.get("extra").asString());
    assertEquals("completed", body.get("state").asString());
    assertEquals(createdBy, body.get("createdBy").asString());
    assertEquals(createdDate, body.get("createdDate").asString());
    assertNotNull(body.get("updatedBy"));
    assertNotNull(body.get("updatedDate"));
    assertEquals(1L, body.get("revision").asLong());
  }

  @Test
  void put_isIdempotent_revisionsIncrementButContentStable() {
    String domain = RandomStringUtils.insecure().nextAlphabetic(5);
    String id = TSID.Factory.getTsid().toString();
    String payload = "{\"description\":\"stable\",\"state\":\"completed\"}";

    HttpResponse first = callback.handle(putRequest("/" + domain + "/" + id, payload));
    HttpResponse second = callback.handle(putRequest("/" + domain + "/" + id, payload));
    HttpResponse third = callback.handle(putRequest("/" + domain + "/" + id, payload));

    assertEquals(201, first.getStatusCode());
    assertEquals(200, second.getStatusCode());
    assertEquals(200, third.getStatusCode());

    JsonNode firstBody = JacksonUtil.readAsTree(first.getBodyAsString());
    JsonNode secondBody = JacksonUtil.readAsTree(second.getBodyAsString());
    JsonNode thirdBody = JacksonUtil.readAsTree(third.getBodyAsString());

    assertEquals("stable", firstBody.get("description").asString());
    assertEquals("stable", secondBody.get("description").asString());
    assertEquals("stable", thirdBody.get("description").asString());

    assertEquals(0L, firstBody.get("revision").asLong());
    assertEquals(1L, secondBody.get("revision").asLong());
    assertEquals(2L, thirdBody.get("revision").asLong());

    assertEquals(
        firstBody.get("createdBy").asString(), thirdBody.get("createdBy").asString());
    assertEquals(
        firstBody.get("createdDate").asString(), thirdBody.get("createdDate").asString());
  }

  @Test
  void put_withMatchingBodyId_succeeds() {
    String domain = RandomStringUtils.insecure().nextAlphabetic(5);
    String id = TSID.Factory.getTsid().toString();

    HttpResponse resp =
        callback.handle(
            putRequest(
                "/" + domain + "/" + id,
                "{\"id\":\"" + id + "\",\"description\":\"matches\"}"));

    assertEquals(201, resp.getStatusCode());
    assertEquals(id, JacksonUtil.readAsTree(resp.getBodyAsString()).get("id").asString());
  }

  @Test
  void put_withMismatchedBodyId_returns400() {
    String domain = RandomStringUtils.insecure().nextAlphabetic(5);
    String urlId = TSID.Factory.getTsid().toString();
    String bodyId = TSID.Factory.getTsid().toString();

    HttpResponse resp =
        callback.handle(
            putRequest(
                "/" + domain + "/" + urlId,
                "{\"id\":\"" + bodyId + "\",\"description\":\"mismatch\"}"));

    assertEquals(400, resp.getStatusCode());
    assertTrue(resp.getBodyAsString().contains("does not match URL id"));
    assertNull(CACHE.get(ctxFor(domain, urlId, null)));
  }

  @Test
  void put_withInvalidJson_returns400() {
    String domain = RandomStringUtils.insecure().nextAlphabetic(5);
    HttpResponse resp =
        callback.handle(putRequest("/" + domain + "/abc", "not-a-json"));
    assertEquals(400, resp.getStatusCode());
  }

  @Test
  void put_withNonObjectBody_returns400() {
    String domain = RandomStringUtils.insecure().nextAlphabetic(5);
    HttpResponse resp = callback.handle(putRequest("/" + domain + "/abc", "[\"array\"]"));
    assertEquals(400, resp.getStatusCode());
    assertTrue(resp.getBodyAsString().contains("must be a JSON object"));
  }

  @Test
  void put_versioned_createsAtVersionZeroWhenVersionAbsent() {
    String path = CATALOG.getPath();
    String id = TSID.Factory.getTsid().toString();

    HttpResponse resp =
        callback.handle(putRequest("/" + path + "/" + id, "{\"description\":\"v0\"}"));

    assertEquals(201, resp.getStatusCode());
    JsonNode body = JacksonUtil.readAsTree(resp.getBodyAsString());
    assertEquals(id, body.get("id").asString());
    assertEquals("0", body.get("version").asString());
    assertEquals("/" + path + "/" + id + ":(version=0)", body.get("href").asString());
  }

  @Test
  void put_versioned_createsAtSpecifiedVersionFromPath() {
    String path = CATALOG.getPath();
    String id = TSID.Factory.getTsid().toString();

    HttpResponse resp =
        callback.handle(
            putRequest(
                "/" + path + "/" + id + ":(version=2.0)",
                "{\"description\":\"v2\"}"));

    assertEquals(201, resp.getStatusCode());
    JsonNode body = JacksonUtil.readAsTree(resp.getBodyAsString());
    assertEquals(id, body.get("id").asString());
    assertEquals("2.0", body.get("version").asString());
    assertEquals("/" + path + "/" + id + ":(version=2.0)", body.get("href").asString());
  }

  @Test
  void put_versioned_replacesExistingAtSpecifiedVersion() {
    String path = CATALOG.getPath();
    String id = TSID.Factory.getTsid().toString();
    callback.handle(
        putRequest(
            "/" + path + "/" + id + ":(version=1.0)",
            "{\"description\":\"first\"}"));

    HttpResponse resp =
        callback.handle(
            putRequest(
                "/" + path + "/" + id + ":(version=1.0)",
                "{\"description\":\"replaced\"}"));

    assertEquals(200, resp.getStatusCode());
    JsonNode body = JacksonUtil.readAsTree(resp.getBodyAsString());
    assertEquals("replaced", body.get("description").asString());
    assertEquals("1.0", body.get("version").asString());
    assertEquals(1L, body.get("revision").asLong());
  }

  @Test
  void put_versioned_withMismatchedBodyVersion_returns400() {
    String path = CATALOG.getPath();
    String id = TSID.Factory.getTsid().toString();

    HttpResponse resp =
        callback.handle(
            putRequest(
                "/" + path + "/" + id + ":(version=1.0)",
                "{\"version\":\"2.0\",\"description\":\"oops\"}"));

    assertEquals(400, resp.getStatusCode());
    assertTrue(resp.getBodyAsString().contains("does not match URL version"));
  }

  @Test
  void put_replace_dropsFieldsNotInBody() {
    String domain = RandomStringUtils.insecure().nextAlphabetic(5);
    String id = TSID.Factory.getTsid().toString();
    callback.handle(
        putRequest(
            "/" + domain + "/" + id,
            "{\"description\":\"first\",\"keepMe\":\"original\"}"));

    HttpResponse resp =
        callback.handle(
            putRequest("/" + domain + "/" + id, "{\"description\":\"second\"}"));

    assertEquals(200, resp.getStatusCode());
    JsonNode body = JacksonUtil.readAsTree(resp.getBodyAsString());
    assertEquals("second", body.get("description").asString());
    assertNull(body.get("keepMe"));
  }

  @Test
  void put_create_persistsToCache() {
    String domain = RandomStringUtils.insecure().nextAlphabetic(5);
    String id = TSID.Factory.getTsid().toString();

    callback.handle(
        putRequest("/" + domain + "/" + id, "{\"description\":\"cached\"}"));

    JsonNode cached = CACHE.get(ctxFor(domain, id, null));
    assertNotNull(cached);
    assertEquals("cached", cached.get("description").asString());
  }

  @Test
  void put_create_returnedHrefIsRequestPath() {
    String domain = RandomStringUtils.insecure().nextAlphabetic(5);
    String id = TSID.Factory.getTsid().toString();

    HttpResponse resp =
        callback.handle(putRequest("/" + domain + "/" + id, "{}"));

    JsonNode body = JacksonUtil.readAsTree(resp.getBodyAsString());
    assertEquals("/" + domain + "/" + id, body.get("href").asString());
    assertFalse(body.get("href").asString().contains(id + "/" + id));
  }

  @Test
  void put_replace_doesNotChangeHref() {
    String domain = RandomStringUtils.insecure().nextAlphabetic(5);
    String id = TSID.Factory.getTsid().toString();
    String originalHref =
        JacksonUtil.readAsTree(
                callback
                    .handle(putRequest("/" + domain + "/" + id, "{\"description\":\"a\"}"))
                    .getBodyAsString())
            .get("href")
            .asString();

    JsonNode replaced =
        JacksonUtil.readAsTree(
            callback
                .handle(putRequest("/" + domain + "/" + id, "{\"description\":\"b\"}"))
                .getBodyAsString());

    assertEquals(originalHref, replaced.get("href").asString());
  }

  @Test
  void put_replace_updatedFieldsDifferFromCreated() {
    String domain = RandomStringUtils.insecure().nextAlphabetic(5);
    String id = TSID.Factory.getTsid().toString();
    JsonNode created =
        JacksonUtil.readAsTree(
            callback
                .handle(putRequest("/" + domain + "/" + id, "{\"description\":\"a\"}"))
                .getBodyAsString());

    JsonNode replaced =
        JacksonUtil.readAsTree(
            callback
                .handle(putRequest("/" + domain + "/" + id, "{\"description\":\"b\"}"))
                .getBodyAsString());

    assertNotNull(replaced.get("updatedBy"));
    assertNotNull(replaced.get("updatedDate"));
    assertNotEquals(
        created.get("createdBy").asString(), replaced.get("updatedBy").asString(),
        "different random user is generated for update vs. create");
  }

  /**
   * Under sustained concurrent PUT to the same yet-unknown URL, the historical check-then-put
   * race let two callbacks both see "not present" and both call {@code CACHE.put}, throwing
   * {@link IllegalArgumentException} and surfacing as {@code 500}. With {@code putIfAbsent},
   * exactly one creator wins with {@code 201} and the losers degrade to an idempotent replace
   * ({@code 200}) on the winning creator's resource — PUT is idempotent, so this convergence
   * is the correct semantic.
   */
  @Test
  void concurrentPutSameNewResource_exactlyOneReturns201_othersReturn200_noneReturn500()
      throws Exception {
    int nThreads = 20;
    String domain = "racedPut-" + UUID.randomUUID();
    String sharedId = "raced-" + UUID.randomUUID();
    String path = "/" + domain + "/" + sharedId;
    String body = "{\"description\":\"hello\"}";

    ExecutorService pool = Executors.newFixedThreadPool(nThreads);
    try {
      CountDownLatch start = new CountDownLatch(1);
      List<Future<Integer>> futures = new ArrayList<>();
      for (int i = 0; i < nThreads; i++) {
        futures.add(
            pool.submit(
                () -> {
                  start.await();
                  return new DynamicPutCallback().handle(putRequest(path, body)).getStatusCode();
                }));
      }
      start.countDown();

      int count201 = 0;
      int count200 = 0;
      for (Future<Integer> f : futures) {
        int status = f.get(5, TimeUnit.SECONDS);
        assertNotEquals(500, status, "Concurrent PUT-create must not leak a 500");
        if (status == 201) count201++;
        if (status == 200) count200++;
      }
      assertEquals(1, count201, "Exactly one concurrent PUT-create must win with 201");
      assertEquals(
          nThreads - 1,
          count200,
          "All losing racers must degrade to replace (200); PUT is idempotent");
    } finally {
      pool.shutdownNow();
    }
  }

  private static HttpRequest putRequest(String path, String body) {
    return new HttpRequest().withPath(path).withBody(body);
  }

  private static RequestContext ctxFor(String domain, String id, String version) {
    String suffix = version == null ? id : id + ":(version=" + version + ")";
    HttpRequest req = new HttpRequest().withPath("/" + domain + "/" + suffix);
    return RequestContext.initialize(req, true, null);
  }
}
