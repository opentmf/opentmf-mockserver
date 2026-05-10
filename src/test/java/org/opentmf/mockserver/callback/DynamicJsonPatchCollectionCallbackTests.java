package org.opentmf.mockserver.callback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentmf.mockserver.util.Constants.ADDITIONAL_FIELDS;
import static org.opentmf.mockserver.util.Constants.CACHE_DURATION_MILLIS;
import static org.opentmf.mockserver.util.Constants.THREE_SECONDS;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.opentmf.mockserver.util.JacksonUtil;
import org.opentmf.mockserver.util.PayloadCache;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith(SystemStubsExtension.class)
class DynamicJsonPatchCollectionCallbackTests {

  private final DynamicJsonPatchCollectionCallback callback =
      new DynamicJsonPatchCollectionCallback();

  @SystemStub
  private static final EnvironmentVariables TEST_ENV_VARIABLES =
      new EnvironmentVariables(CACHE_DURATION_MILLIS, THREE_SECONDS, ADDITIONAL_FIELDS, "project");

  @Test
  void bulkCreate_returnsArrayOfFullResources() {
    String domain = RandomStringUtils.randomAlphabetic(5);
    HttpResponse resp =
        callback.handle(patchRequest(domain, "["
            + addOp("{\"description\":\"first\"}") + ","
            + addOp("{\"description\":\"second\"}")
            + "]"));

    assertEquals(200, resp.getStatusCode());
    JsonNode body = JacksonUtil.readAsTree(resp.getBodyAsString());
    assertTrue(body.isArray());
    ArrayNode array = (ArrayNode) body;
    assertEquals(2, array.size());
    for (JsonNode item : array) {
      assertNotNull(item.get("id"));
      assertFalse(item.get("id").asText().isEmpty());
      assertNotNull(item.get("href"));
      assertNotNull(item.get("state"));
      assertNotNull(item.get("createdBy"));
      assertNotNull(item.get("createdDate"));
      assertNotNull(item.get("revision"));
      assertNotNull(item.get("project"));
    }
    assertEquals("first", array.get(0).get("description").asText());
    assertEquals("second", array.get(1).get("description").asText());
    assertEquals(2, PayloadCache.getInstance().getAll(domain).size());
  }

  @Test
  void bulkCreate_withFieldsNone_returnsIdAndHrefOnly() {
    String domain = RandomStringUtils.randomAlphabetic(5);
    HttpRequest req = patchRequest(domain, "["
        + addOp("{\"description\":\"a\"}") + ","
        + addOp("{\"description\":\"b\"}")
        + "]");
    req.withQueryStringParameter("fields", "none");

    HttpResponse resp = callback.handle(req);

    assertEquals(200, resp.getStatusCode());
    ArrayNode array = (ArrayNode) JacksonUtil.readAsTree(resp.getBodyAsString());
    assertEquals(2, array.size());
    for (JsonNode item : array) {
      assertEquals(2, item.size());
      assertNotNull(item.get("id"));
      assertNotNull(item.get("href"));
      assertNull(item.get("description"));
      assertNull(item.get("state"));
      assertNull(item.get("project"));
    }
  }

  @Test
  void bulkCreate_withFieldsList_returnsProjectedFieldsPlusIdAndHref() {
    String domain = RandomStringUtils.randomAlphabetic(5);
    HttpRequest req = patchRequest(domain, "["
        + addOp("{\"description\":\"a\",\"name\":\"alpha\"}")
        + "]");
    req.withQueryStringParameter("fields", "description");

    HttpResponse resp = callback.handle(req);

    assertEquals(200, resp.getStatusCode());
    ArrayNode array = (ArrayNode) JacksonUtil.readAsTree(resp.getBodyAsString());
    JsonNode item = array.get(0);
    assertNotNull(item.get("id"));
    assertNotNull(item.get("href"));
    assertEquals("a", item.get("description").asText());
    assertNull(item.get("name"));
    assertNull(item.get("createdBy"));
  }

  @Test
  void bulkCreate_withExplicitId_honorsId() {
    String domain = RandomStringUtils.randomAlphabetic(5);
    HttpResponse resp =
        callback.handle(patchRequest(domain, "["
            + addOp("{\"id\":\"item-1\",\"description\":\"a\"}")
            + "]"));

    assertEquals(200, resp.getStatusCode());
    ArrayNode array = (ArrayNode) JacksonUtil.readAsTree(resp.getBodyAsString());
    assertEquals("item-1", array.get(0).get("id").asText());
    assertTrue(array.get(0).get("href").asText().endsWith("/" + domain + "/item-1"));
  }

  @Test
  void bulkCreate_withDuplicateIdInBatch_returns409_andNothingCached() {
    String domain = RandomStringUtils.randomAlphabetic(5);
    HttpResponse resp =
        callback.handle(patchRequest(domain, "["
            + addOp("{\"id\":\"dup\"}") + ","
            + addOp("{\"id\":\"dup\"}")
            + "]"));

    assertEquals(409, resp.getStatusCode());
    assertTrue(resp.getBodyAsString().contains("dup"));
    assertEquals(0, PayloadCache.getInstance().getAll(domain).size());
  }

  @Test
  void bulkCreate_withIdAlreadyInCache_returns409_atomicAbort() {
    String domain = RandomStringUtils.randomAlphabetic(5);
    // pre-populate the cache via the bulk callback itself
    callback.handle(patchRequest(domain, "[" + addOp("{\"id\":\"existing\"}") + "]"));
    int sizeBefore = PayloadCache.getInstance().getAll(domain).size();

    HttpResponse resp =
        callback.handle(patchRequest(domain, "["
            + addOp("{\"id\":\"new1\"}") + ","
            + addOp("{\"id\":\"existing\"}") + ","
            + addOp("{\"id\":\"new2\"}")
            + "]"));

    assertEquals(409, resp.getStatusCode());
    // atomic: sizes unchanged, "new1" and "new2" must not have been committed
    assertEquals(sizeBefore, PayloadCache.getInstance().getAll(domain).size());
  }

  @Test
  void bulkCreate_withNonAddOp_returns400() {
    String domain = RandomStringUtils.randomAlphabetic(5);
    HttpResponse resp =
        callback.handle(patchRequest(domain, "[{\"op\":\"remove\",\"path\":\"/foo\"}]"));

    assertEquals(400, resp.getStatusCode());
    assertTrue(resp.getBodyAsString().contains("only 'add' is supported"));
    assertEquals(0, PayloadCache.getInstance().getAll(domain).size());
  }

  @Test
  void bulkCreate_withNonRootPath_returns400() {
    String domain = RandomStringUtils.randomAlphabetic(5);
    HttpResponse resp =
        callback.handle(patchRequest(domain, "["
            + "{\"op\":\"add\",\"path\":\"/-\",\"value\":{}}"
            + "]"));

    assertEquals(400, resp.getStatusCode());
    assertTrue(resp.getBodyAsString().contains("path must be '/'"));
  }

  @Test
  void bulkCreate_withBodyNotArray_returns400() {
    String domain = RandomStringUtils.randomAlphabetic(5);
    HttpResponse resp = callback.handle(patchRequest(domain, "{\"not\":\"an array\"}"));

    assertEquals(400, resp.getStatusCode());
    assertTrue(resp.getBodyAsString().contains("JSON array"));
  }

  @Test
  void bulkCreate_withEmptyArray_returns400() {
    String domain = RandomStringUtils.randomAlphabetic(5);
    HttpResponse resp = callback.handle(patchRequest(domain, "[]"));

    assertEquals(400, resp.getStatusCode());
    assertTrue(resp.getBodyAsString().contains("at least one"));
  }

  @Test
  void bulkCreate_withValueNotObject_returns400() {
    String domain = RandomStringUtils.randomAlphabetic(5);
    HttpResponse resp =
        callback.handle(patchRequest(domain, "["
            + "{\"op\":\"add\",\"path\":\"/\",\"value\":\"not-an-object\"}"
            + "]"));

    assertEquals(400, resp.getStatusCode());
    assertTrue(resp.getBodyAsString().contains("'value' must be a JSON object"));
  }

  @Test
  void bulkCreate_withInvalidJson_returns400() {
    String domain = RandomStringUtils.randomAlphabetic(5);
    HttpResponse resp = callback.handle(patchRequest(domain, "not json"));

    assertEquals(400, resp.getStatusCode());
  }

  private static HttpRequest patchRequest(String domain, String body) {
    return new HttpRequest().withPath("/" + domain).withBody(body);
  }

  private static String addOp(String valueJson) {
    return "{\"op\":\"add\",\"path\":\"/\",\"value\":" + valueJson + "}";
  }
}
