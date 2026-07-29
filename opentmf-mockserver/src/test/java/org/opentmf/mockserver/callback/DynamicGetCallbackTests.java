package org.opentmf.mockserver.callback;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.opentmf.mockserver.util.Constants.ADDITIONAL_FIELDS;
import static org.opentmf.mockserver.util.Constants.CACHE_DURATION_MILLIS;
import static org.opentmf.mockserver.util.Constants.THREE_SECONDS;

import io.hypersistence.tsid.TSID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.opentmf.mockserver.model.RequestContext;
import org.opentmf.mockserver.util.JacksonUtil;
import org.opentmf.mockserver.util.PayloadCache;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith(SystemStubsExtension.class)
class DynamicGetCallbackTests {

  private DynamicGetCallback dynamicGetCallback;

  @SystemStub
  private static final EnvironmentVariables TEST_ENV_VARIABLES =
      new EnvironmentVariables(CACHE_DURATION_MILLIS, THREE_SECONDS, ADDITIONAL_FIELDS, "project");

  @BeforeEach
  void setup() {
    dynamicGetCallback = new DynamicGetCallback();
  }

  @Test
  void shouldReturnCompletedWhenStatusIsAcknowledged() {
    // Given
    String domain = "serviceInventory";
    String id = TSID.Factory.getTsid().toString();
    addDataToCache(domain, id, "created");

    HttpRequest httpRequest = new HttpRequest().withPath("/" + domain + "/" + id);

    // When
    HttpResponse httpResponse = dynamicGetCallback.handle(httpRequest);

    // Then
    assertEquals(200, httpResponse.getStatusCode());
    JsonNode responseJson = JacksonUtil.readAsTree(httpResponse.getBodyAsString());
    assertEquals("active", responseJson.get("status").asText());
  }

  @Test
  void shouldReturnCompletedWhenStatusIsAcknowledged1() {
    // Given
    String domain = "serviceInventory";
    String id = TSID.Factory.getTsid().toString();
    addDataToCache(domain, id, "created");

    HttpRequest httpRequest =
        new HttpRequest()
            .withPath("/" + domain + "/" + id)
            .withQueryStringParameter("fields", "id");

    // When
    HttpResponse httpResponse = dynamicGetCallback.handle(httpRequest);

    // Then
    assertEquals(200, httpResponse.getStatusCode());
    JsonNode responseJson = JacksonUtil.readAsTree(httpResponse.getBodyAsString());
    assertEquals(id, responseJson.get("id").asText());
    assertNull(responseJson.get("status"));
  }

  @Test
  void shouldReturnOnlyIdAndHrefWhenFieldsIsNone() {
    // Given
    String domain = "serviceInventory";
    String id = TSID.Factory.getTsid().toString();
    addDataToCache(domain, id, "created");

    HttpRequest httpRequest =
        new HttpRequest()
            .withPath("/" + domain + "/" + id)
            .withQueryStringParameter("fields", "none");

    // When
    HttpResponse httpResponse = dynamicGetCallback.handle(httpRequest);

    // Then
    assertEquals(200, httpResponse.getStatusCode());
    JsonNode responseJson = JacksonUtil.readAsTree(httpResponse.getBodyAsString());
    assertEquals(id, responseJson.get("id").asText());
    assertNotNull(responseJson.get("href"));
    assertNull(responseJson.get("status"));
    assertNull(responseJson.get("createdDate"));
    assertNull(responseJson.get("createdBy"));
    assertNull(responseJson.get("revision"));
    assertEquals(2, responseJson.size());
  }

  @Test
  void shouldReturnOnlyIdAndHrefWhenFieldsIsNoneCaseInsensitive() {
    // Given
    String domain = "serviceInventory";
    String id = TSID.Factory.getTsid().toString();
    addDataToCache(domain, id, "created");

    HttpRequest httpRequest =
        new HttpRequest()
            .withPath("/" + domain + "/" + id)
            .withQueryStringParameter("fields", "NONE");

    // When
    HttpResponse httpResponse = dynamicGetCallback.handle(httpRequest);

    // Then
    assertEquals(200, httpResponse.getStatusCode());
    JsonNode responseJson = JacksonUtil.readAsTree(httpResponse.getBodyAsString());
    assertEquals(2, responseJson.size());
    assertNotNull(responseJson.get("id"));
    assertNotNull(responseJson.get("href"));
  }

  @Test
  void shouldReturnNotFoundWhenDataDoesNotExist() {
    // Given
    String domain = "testDomain";
    String id = TSID.Factory.getTsid().toString();

    HttpRequest httpRequest = new HttpRequest().withPath("/" + domain + "/" + id);

    // When
    HttpResponse httpResponse = dynamicGetCallback.handle(httpRequest);

    // Then
    assertEquals(notFoundResponse().getStatusCode(), httpResponse.getStatusCode());
  }

  @Test
  void shouldReturnDataWhenExists() {
    // Given
    String domain = "testDomain";
    String id = TSID.Factory.getTsid().toString();
    addDataToCache(domain, id, "completed");

    HttpRequest httpRequest = new HttpRequest().withPath("/" + domain + "/" + id);

    // When
    HttpResponse httpResponse = dynamicGetCallback.handle(httpRequest);

    // Then
    assertEquals(200, httpResponse.getStatusCode());
    JsonNode responseJson = JacksonUtil.readAsTree(httpResponse.getBodyAsString());
    assertNotNull(responseJson);
  }

  @Test
  void shouldNotChangeStatusWhenNotAcknowledged() {
    // Given
    String domain = "testDomain";
    String id = TSID.Factory.getTsid().toString();
    addDataToCache(domain, id, "completed");

    HttpRequest httpRequest = new HttpRequest().withPath("/" + domain + "/" + id);

    // When
    HttpResponse httpResponse = dynamicGetCallback.handle(httpRequest);

    // Then
    assertEquals(200, httpResponse.getStatusCode());
    JsonNode responseJson = JacksonUtil.readAsTree(httpResponse.getBodyAsString());
    assertEquals("completed", responseJson.get("status").asText());
  }

  @Test
  void testHandle_whenIdFromPayloadAndCacheNotNull() {
    // Given
    DynamicPostCallback dynamicPostCallback = new DynamicPostCallback();
    String idFromPayload = "testId";
    String domain = "testDomain";

    HttpRequest httpRequest = new HttpRequest();
    httpRequest.withBody("{ \"id\": \"" + idFromPayload + "\" }");
    httpRequest.withPath("/" + domain);

    // When
    HttpResponse httpResponse = dynamicPostCallback.handle(httpRequest);
    assertEquals(201, httpResponse.getStatusCode());

    // Then
    HttpResponse httpResponseSameId = dynamicPostCallback.handle(httpRequest);
    assertEquals(400, httpResponseSameId.getStatusCode());
    assertEquals(
        "{\"code\":400,\"message\":\"[id='testId', version=null] already exists.\",\"status\":\"BAD_REQUEST_400\"}",
        httpResponseSameId.getBodyAsString());
  }

  /**
   * The state transition on a first GET must NOT mutate the cached JsonNode in place. Concurrent
   * list-GET readers iterate the snapshot returned by {@link PayloadCache#getAll} outside the
   * cache lock and share the same JsonNode references — an in-place put/increment here would
   * race Jackson's non-thread-safe internal LinkedHashMap iteration on those readers. The write
   * must land on a deep-copy and replace the cached reference atomically.
   */
  @Test
  void stateTransitionOnGet_replacesCachedReference_neverMutatesInPlace() {
    // Given: an entity in the initial ("created") state.
    String domain = "serviceInventory";
    String id = TSID.Factory.getTsid().toString();
    addDataToCache(domain, id, "created");

    HttpRequest lookupRequest = new HttpRequest().withPath("/" + domain + "/" + id);
    RequestContext lookupCtx = RequestContext.initialize(lookupRequest, true, null);
    JsonNode preTransitionReference = PayloadCache.getInstance().get(lookupCtx);
    assertNotNull(preTransitionReference);
    assertEquals("created", preTransitionReference.get("status").asText());

    // When: a GET triggers the state transition (created → active).
    HttpResponse httpResponse = dynamicGetCallback.handle(lookupRequest);

    // Then: the response reflects the transition (existing behaviour preserved).
    assertEquals(200, httpResponse.getStatusCode());
    JsonNode responseJson = JacksonUtil.readAsTree(httpResponse.getBodyAsString());
    assertEquals("active", responseJson.get("status").asText());

    // And: the reference we captured BEFORE the transition must still read "created" — no
    // in-place mutation happened on the shared cached node. This is what makes the 2.1.7
    // snapshot fix safe.
    assertEquals(
        "created",
        preTransitionReference.get("status").asText(),
        "Cached JsonNode was mutated in place; concurrent list-GET readers holding a snapshot "
            + "would race Jackson's internal LinkedHashMap iteration");

    // And: the cache now holds a fresh reference with the transitioned state.
    JsonNode postTransitionReference = PayloadCache.getInstance().get(lookupCtx);
    assertNotSame(
        preTransitionReference,
        postTransitionReference,
        "Cache should have been updated by reference replacement, not in-place mutation");
    assertEquals("active", postTransitionReference.get("status").asText());
  }

  private void addDataToCache(String domain, String id, String status) {
    ObjectNode node = JacksonUtil.createObjectNode();
    node.put("status", status);
    node.put("id", id);

    HttpRequest httpRequest =
        new HttpRequest().withPath("/" + domain).withBody(JacksonUtil.writeAsString(node));

    DynamicPostCallback dynamicPostCallback = new DynamicPostCallback();
    HttpResponse httpResponse = dynamicPostCallback.handle(httpRequest);
    assertEquals(201, httpResponse.getStatusCode());
  }
}
