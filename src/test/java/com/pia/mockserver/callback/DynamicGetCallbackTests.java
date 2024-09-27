package com.pia.mockserver.callback;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockserver.model.HttpResponse.notFoundResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pia.mockserver.util.JacksonUtil;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

class DynamicGetCallbackTests {

  private DynamicGetCallback dynamicGetCallback;

  @BeforeEach
  public void setup() {
    dynamicGetCallback = new DynamicGetCallback();
  }

  @Test
  void shouldReturnCompletedWhenStatusIsAcknowledged() {
    // Given
    String domain = "serviceInventory";
    String id = UUID.randomUUID().toString();
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
    String id = UUID.randomUUID().toString();
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
  void shouldReturnNotFoundWhenDataDoesNotExist() {
    // Given
    String domain = "testDomain";
    String id = UUID.randomUUID().toString();

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
    String id = UUID.randomUUID().toString();
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
    String id = UUID.randomUUID().toString();
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
    assertEquals(200, httpResponse.getStatusCode());

    // Then
    HttpResponse httpResponseSameId = dynamicPostCallback.handle(httpRequest);
    assertEquals(400, httpResponseSameId.getStatusCode());
    assertEquals(
        "{\"code\":400,\"message\":\"This id is already exist. id: "
            + idFromPayload
            + "\",\"status\":\"BAD_REQUEST_400\"}",
        httpResponseSameId.getBodyAsString());
  }

  private void addDataToCache(String domain, String id, String status) {
    ObjectNode node = JacksonUtil.createObjectNode();
    node.put("status", status);
    node.put("id", id);

    HttpRequest httpRequest =
        new HttpRequest().withPath("/" + domain).withBody(JacksonUtil.writeAsString(node));

    DynamicPostCallback dynamicPostCallback = new DynamicPostCallback();
    HttpResponse httpResponse = dynamicPostCallback.handle(httpRequest);
    assertEquals(200, httpResponse.getStatusCode());
  }
}
