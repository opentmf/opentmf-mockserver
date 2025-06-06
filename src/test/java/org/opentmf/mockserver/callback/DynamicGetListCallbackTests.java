package org.opentmf.mockserver.callback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentmf.mockserver.util.Constants.ADDITIONAL_FIELDS;
import static org.opentmf.mockserver.util.Constants.CACHE_DURATION_MILLIS;
import static org.opentmf.mockserver.util.Constants.THREE_SECONDS;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Random;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.opentmf.mockserver.util.JacksonUtil;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith(SystemStubsExtension.class)
class DynamicGetListCallbackTests {

  private final DynamicGetListCallback dynamicGetListCallback = new DynamicGetListCallback();

  @SystemStub
  private static final EnvironmentVariables TEST_ENV_VARIABLES =
      new EnvironmentVariables(
          CACHE_DURATION_MILLIS, THREE_SECONDS,
          ADDITIONAL_FIELDS, "project"
      );

  @Test
  void testResponseWithValidParameters() {
    // Given
    String domain = RandomStringUtils.randomAlphabetic(5);
    addDataToCache(domain, 10);
    HttpRequest httpRequest =
        new HttpRequest()
            .withPath("/" + domain)
            .withQueryStringParameter("limit", "1")
            .withQueryStringParameter("offset", "0")
            .withQueryStringParameter("sort", "createdDate");

    // When
    HttpResponse httpResponse = dynamicGetListCallback.handle(httpRequest);

    // Then
    assertEquals(200, httpResponse.getStatusCode());
    JsonNode responseJson = JacksonUtil.readAsTree(httpResponse.getBodyAsString());
    assertTrue(responseJson.isArray());
    ArrayNode arrayNode = (ArrayNode) responseJson;
    assertEquals(1, arrayNode.size());
  }

  @Test
  void testResponseWithMissingParameters() {
    // Given
    String domain = RandomStringUtils.randomAlphabetic(5);
    addDataToCache(domain, 30);
    HttpRequest httpRequest = new HttpRequest().withPath("/" + domain);

    // When
    HttpResponse httpResponse = dynamicGetListCallback.handle(httpRequest);

    // Then
    assertEquals(200, httpResponse.getStatusCode());
    JsonNode responseJson = JacksonUtil.readAsTree(httpResponse.getBodyAsString());
    assertTrue(responseJson.isArray());
    ArrayNode arrayNode = (ArrayNode) responseJson;
    assertEquals(10, arrayNode.size());
  }

  @Test
  void testResponseWithMultipleDataAndValidParameters() {
    // Given
    String domain = RandomStringUtils.randomAlphabetic(5);
    addDataToCache(domain, 10);
    HttpRequest httpRequest =
        new HttpRequest()
            .withPath("/" + domain)
            .withQueryStringParameter("limit", "3")
            .withQueryStringParameter("offset", "0")
            .withQueryStringParameter("sort", "-randomNumber");

    // When
    HttpResponse httpResponse = dynamicGetListCallback.handle(httpRequest);

    // Then
    assertEquals(200, httpResponse.getStatusCode());
    JsonNode responseJson = JacksonUtil.readAsTree(httpResponse.getBodyAsString());
    assertTrue(responseJson.isArray());
    ArrayNode arrayNode = (ArrayNode) responseJson;
    assertEquals(3, arrayNode.size());
  }

  @Test
  void testResponseWithProvidedLimit() {
    // Given
    String domain = RandomStringUtils.randomAlphabetic(5);
    addDataToCache(domain, 10);
    HttpRequest httpRequest =
        new HttpRequest()
            .withPath("/" + domain)
            .withQueryStringParameter("limit", "5")
            .withQueryStringParameter("offset", "0")
            .withQueryStringParameter("sort", "createdDate");

    // When
    HttpResponse httpResponse = dynamicGetListCallback.handle(httpRequest);

    // Then
    assertEquals(200, httpResponse.getStatusCode());
    JsonNode responseJson = JacksonUtil.readAsTree(httpResponse.getBodyAsString());
    assertTrue(responseJson.isArray());
    ArrayNode arrayNode = (ArrayNode) responseJson;
    assertEquals(5, arrayNode.size());
  }

  @Test
  void testResponseWithProvidedOffset() {
    // Given
    String domain = RandomStringUtils.randomAlphabetic(5);
    addDataToCache(domain, 10);
    HttpRequest httpRequest =
        new HttpRequest()
            .withPath("/" + domain)
            .withQueryStringParameter("limit", "5")
            .withQueryStringParameter("offset", "5")
            .withQueryStringParameter("sort", "createdDate");

    // When
    HttpResponse httpResponse = dynamicGetListCallback.handle(httpRequest);

    // Then
    assertEquals(200, httpResponse.getStatusCode());
    JsonNode responseJson = JacksonUtil.readAsTree(httpResponse.getBodyAsString());
    assertTrue(responseJson.isArray());
    ArrayNode arrayNode = (ArrayNode) responseJson;
    assertEquals(5, arrayNode.size());
  }

  @Test
  void testResponseWithProvidedSort() {
    // Given
    String domain = RandomStringUtils.randomAlphabetic(5);
    addDataToCache(domain, 30);
    HttpRequest httpRequest =
        new HttpRequest()
            .withPath("/" + domain)
            .withQueryStringParameter("limit", "10")
            .withQueryStringParameter("offset", "0")
            .withQueryStringParameter("sort", "-orderNumber");

    // When
    HttpResponse httpResponse = dynamicGetListCallback.handle(httpRequest);

    // Then
    assertEquals(200, httpResponse.getStatusCode());
    JsonNode responseJson = JacksonUtil.readAsTree(httpResponse.getBodyAsString());
    assertTrue(responseJson.isArray());
    ArrayNode arrayNode = (ArrayNode) responseJson;
    assertEquals(10, arrayNode.size());

    // Check if the array is sorted in descending order
    int previousValue = arrayNode.get(0).get("orderNumber").asInt();
    for (int i = 1; i < arrayNode.size(); i++) {
      int currentValue = arrayNode.get(i).get("orderNumber").asInt();
      assertTrue(previousValue >= currentValue);
      previousValue = currentValue;
    }
  }

  @Test
  void testHandleWithFilter() {
    // Given
    String domain = RandomStringUtils.randomAlphabetic(5);
    addDataToCache(domain, 10);
    HttpRequest httpRequest =
        new HttpRequest()
            .withPath("/" + domain)
            .withQueryStringParameter("filter", "$[?(@.description == 'test_getAll')]");

    // When
    HttpResponse httpResponse = dynamicGetListCallback.handle(httpRequest);

    // Then
    assertEquals(200, httpResponse.getStatusCode());
    JsonNode responseJson = JacksonUtil.readAsTree(httpResponse.getBodyAsString());
    assertTrue(responseJson.isArray());
    ArrayNode arrayNode = (ArrayNode) responseJson;
    assertEquals(10, arrayNode.size());
  }

  @Test
  void testHandleWithFields() {
    // Given
    String domain = RandomStringUtils.randomAlphabetic(5);
    addDataToCache(domain, 10);
    HttpRequest httpRequest =
        new HttpRequest()
            .withPath("/" + domain)
            .withQueryStringParameter("fields", "description,name");

    // When
    HttpResponse httpResponse = dynamicGetListCallback.handle(httpRequest);

    // Then
    assertEquals(200, httpResponse.getStatusCode());
    JsonNode responseJson = JacksonUtil.readAsTree(httpResponse.getBodyAsString());
    assertTrue(responseJson.isArray());
    ArrayNode arrayNode = (ArrayNode) responseJson;
    assertEquals(10, arrayNode.size());
    for (JsonNode node : arrayNode) {
      assertTrue(node.has("description"));
      assertTrue(node.has("name"));
      assertFalse(node.has("randomNumber"));
      assertFalse(node.has("orderNumber"));
    }
  }

  @Test
  void testHandleWithSort() {
    // Given
    String domain = RandomStringUtils.randomAlphabetic(5);
    addDataToCache(domain, 10);
    HttpRequest httpRequest =
        new HttpRequest().withPath("/" + domain).withQueryStringParameter("sort", "-randomNumber");

    // When
    HttpResponse httpResponse = dynamicGetListCallback.handle(httpRequest);

    // Then
    assertEquals(200, httpResponse.getStatusCode());
    JsonNode responseJson = JacksonUtil.readAsTree(httpResponse.getBodyAsString());
    assertTrue(responseJson.isArray());
    ArrayNode arrayNode = (ArrayNode) responseJson;
    assertEquals(10, arrayNode.size());

    // Check if the array is sorted in descending order
    int previousValue = arrayNode.get(0).get("randomNumber").asInt();
    for (int i = 1; i < arrayNode.size(); i++) {
      int currentValue = arrayNode.get(i).get("randomNumber").asInt();
      assertTrue(previousValue >= currentValue);
      previousValue = currentValue;
    }
  }

  @Test
  void testHandleWithBooleanSort() {
    // Given
    String domain = RandomStringUtils.randomAlphabetic(5);
    addDataToCache(domain, 11);
    HttpRequest httpRequest =
        new HttpRequest()
            .withPath("/" + domain)
            .withQueryStringParameter("limit", "10")
            .withQueryStringParameter("sort", "-isEven");

    // When
    HttpResponse httpResponse = dynamicGetListCallback.handle(httpRequest);

    // Then
    assertEquals(200, httpResponse.getStatusCode());
    JsonNode responseJson = JacksonUtil.readAsTree(httpResponse.getBodyAsString());
    assertTrue(responseJson.isArray());
    ArrayNode arrayNode = (ArrayNode) responseJson;
    assertEquals(10, arrayNode.size());

    // Check if the array is sorted in descending order
    for (int i = 0; i < arrayNode.size(); i++) {
      int orderNumber = arrayNode.get(i).get("orderNumber").asInt();
      if (orderNumber % 2 == 0) {
        assertTrue(arrayNode.get(i).get("isEven").asBoolean());
      } else {
        assertFalse(arrayNode.get(i).get("isEven").asBoolean());
      }
    }
  }

  private void addDataToCache(String domain, int count) {
    for (int i = 0; i < count; i++) {

      ObjectNode node = JacksonUtil.createObjectNode();
      node.put("description", "test_getAll");
      node.put("name", "test");
      node.put("randomNumber", new Random().nextInt(5));
      node.put("isEven", i % 2 == 0);
      node.put("orderNumber", i);
      node.put("bigRandomNumber", new Random().nextInt(1000));

      post(new HttpRequest().withPath("/" + domain).withBody(JacksonUtil.writeAsString(node)));
    }
  }

  private final DynamicPostCallback dynamicPostCallback = new DynamicPostCallback();

  void post(HttpRequest httpRequest) {
    HttpResponse handle = dynamicPostCallback.handle(httpRequest);
    assertEquals(200, handle.getStatusCode());
  }
}
