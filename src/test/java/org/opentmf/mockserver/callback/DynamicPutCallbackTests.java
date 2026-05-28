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
    String domain = RandomStringUtils.randomAlphabetic(5);
    String id = TSID.Factory.getTsid().toString();

    HttpResponse resp =
        callback.handle(
            putRequest("/" + domain + "/" + id, "{\"description\":\"hello\"}"));

    assertEquals(201, resp.getStatusCode());
    JsonNode body = JacksonUtil.readAsTree(resp.getBodyAsString());
    assertEquals(id, body.get("id").asText());
    assertEquals("/" + domain + "/" + id, body.get("href").asText());
    assertEquals("hello", body.get("description").asText());
    assertNotNull(body.get("createdBy"));
    assertNotNull(body.get("createdDate"));
    assertEquals(0L, body.get("revision").asLong());
    assertNotNull(body.get("project"));
    assertEquals("acknowledged", body.get("state").asText());
    assertNull(body.get("updatedBy"));
    assertNull(body.get("updatedDate"));
    assertNotNull(CACHE.get(ctxFor(domain, id, null)));
  }

  @Test
  void put_whenInCache_replacesAndReturns200() {
    String domain = RandomStringUtils.randomAlphabetic(5);
    String id = TSID.Factory.getTsid().toString();
    JsonNode created =
        JacksonUtil.readAsTree(
            callback
                .handle(putRequest("/" + domain + "/" + id, "{\"description\":\"v1\"}"))
                .getBodyAsString());
    String createdBy = created.get("createdBy").asText();
    String createdDate = created.get("createdDate").asText();

    HttpResponse resp =
        callback.handle(
            putRequest(
                "/" + domain + "/" + id,
                "{\"description\":\"v2\",\"extra\":\"new\",\"state\":\"completed\"}"));

    assertEquals(200, resp.getStatusCode());
    JsonNode body = JacksonUtil.readAsTree(resp.getBodyAsString());
    assertEquals(id, body.get("id").asText());
    assertEquals("/" + domain + "/" + id, body.get("href").asText());
    assertEquals("v2", body.get("description").asText());
    assertEquals("new", body.get("extra").asText());
    assertEquals("completed", body.get("state").asText());
    assertEquals(createdBy, body.get("createdBy").asText());
    assertEquals(createdDate, body.get("createdDate").asText());
    assertNotNull(body.get("updatedBy"));
    assertNotNull(body.get("updatedDate"));
    assertEquals(1L, body.get("revision").asLong());
  }

  @Test
  void put_isIdempotent_revisionsIncrementButContentStable() {
    String domain = RandomStringUtils.randomAlphabetic(5);
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

    assertEquals("stable", firstBody.get("description").asText());
    assertEquals("stable", secondBody.get("description").asText());
    assertEquals("stable", thirdBody.get("description").asText());

    assertEquals(0L, firstBody.get("revision").asLong());
    assertEquals(1L, secondBody.get("revision").asLong());
    assertEquals(2L, thirdBody.get("revision").asLong());

    assertEquals(
        firstBody.get("createdBy").asText(), thirdBody.get("createdBy").asText());
    assertEquals(
        firstBody.get("createdDate").asText(), thirdBody.get("createdDate").asText());
  }

  @Test
  void put_withMatchingBodyId_succeeds() {
    String domain = RandomStringUtils.randomAlphabetic(5);
    String id = TSID.Factory.getTsid().toString();

    HttpResponse resp =
        callback.handle(
            putRequest(
                "/" + domain + "/" + id,
                "{\"id\":\"" + id + "\",\"description\":\"matches\"}"));

    assertEquals(201, resp.getStatusCode());
    assertEquals(id, JacksonUtil.readAsTree(resp.getBodyAsString()).get("id").asText());
  }

  @Test
  void put_withMismatchedBodyId_returns400() {
    String domain = RandomStringUtils.randomAlphabetic(5);
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
    String domain = RandomStringUtils.randomAlphabetic(5);
    HttpResponse resp =
        callback.handle(putRequest("/" + domain + "/abc", "not-a-json"));
    assertEquals(400, resp.getStatusCode());
  }

  @Test
  void put_withNonObjectBody_returns400() {
    String domain = RandomStringUtils.randomAlphabetic(5);
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
    assertEquals(id, body.get("id").asText());
    assertEquals("0", body.get("version").asText());
    assertEquals("/" + path + "/" + id + ":(version=0)", body.get("href").asText());
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
    assertEquals(id, body.get("id").asText());
    assertEquals("2.0", body.get("version").asText());
    assertEquals("/" + path + "/" + id + ":(version=2.0)", body.get("href").asText());
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
    assertEquals("replaced", body.get("description").asText());
    assertEquals("1.0", body.get("version").asText());
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
    String domain = RandomStringUtils.randomAlphabetic(5);
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
    assertEquals("second", body.get("description").asText());
    assertNull(body.get("keepMe"));
  }

  @Test
  void put_create_persistsToCache() {
    String domain = RandomStringUtils.randomAlphabetic(5);
    String id = TSID.Factory.getTsid().toString();

    callback.handle(
        putRequest("/" + domain + "/" + id, "{\"description\":\"cached\"}"));

    JsonNode cached = CACHE.get(ctxFor(domain, id, null));
    assertNotNull(cached);
    assertEquals("cached", cached.get("description").asText());
  }

  @Test
  void put_create_returnedHrefIsRequestPath() {
    String domain = RandomStringUtils.randomAlphabetic(5);
    String id = TSID.Factory.getTsid().toString();

    HttpResponse resp =
        callback.handle(putRequest("/" + domain + "/" + id, "{}"));

    JsonNode body = JacksonUtil.readAsTree(resp.getBodyAsString());
    assertEquals("/" + domain + "/" + id, body.get("href").asText());
    assertFalse(body.get("href").asText().contains(id + "/" + id));
  }

  @Test
  void put_replace_doesNotChangeHref() {
    String domain = RandomStringUtils.randomAlphabetic(5);
    String id = TSID.Factory.getTsid().toString();
    String originalHref =
        JacksonUtil.readAsTree(
                callback
                    .handle(putRequest("/" + domain + "/" + id, "{\"description\":\"a\"}"))
                    .getBodyAsString())
            .get("href")
            .asText();

    JsonNode replaced =
        JacksonUtil.readAsTree(
            callback
                .handle(putRequest("/" + domain + "/" + id, "{\"description\":\"b\"}"))
                .getBodyAsString());

    assertEquals(originalHref, replaced.get("href").asText());
  }

  @Test
  void put_replace_updatedFieldsDifferFromCreated() {
    String domain = RandomStringUtils.randomAlphabetic(5);
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
        created.get("createdBy").asText(), replaced.get("updatedBy").asText(),
        "different random user is generated for update vs. create");
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
