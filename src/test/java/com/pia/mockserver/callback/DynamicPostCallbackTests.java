package com.pia.mockserver.callback;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pia.mockserver.util.JacksonUtil;
import com.pia.mockserver.util.PayloadCache;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

class DynamicPostCallbackTests {

  private static final PayloadCache CACHE = PayloadCache.getInstance();
  private DynamicPostCallback callback;

  private HttpRequest httpRequest;

  @BeforeEach
  void setUp() {
    callback = new DynamicPostCallback();
    httpRequest = new HttpRequest();
  }

  @Test
  void shouldReturnAcknowledgedServiceOrder() {
    // Given
    String requestBody =
        "{\n"
            + "  \"category\": \"UNITY\",\n"
            + "  \"@type\": \"ServiceOrder\",\n"
            + "  \"serviceOrderItem\": [\n"
            + "    {\n"
            + "      \"id\": \"100\",\n"
            + "      \"action\": \"add\",\n"
            + "      \"service\": {\n"
            + "        \"serviceType\": \"ucc.unity.license\",\n"
            + "        \"serviceCharacteristic\": [\n"
            + "          {\n"
            + "            \"name\": \"Quantity\",\n"
            + "            \"valueType\": \"integer\",\n"
            + "            \"value\": 10\n"
            + "          }\n"
            + "        ],\n"
            + "        \"supportingResource\": [\n"
            + "          {\n"
            + "            \"id\": \"LC_DL-UNL_50\"\n"
            + "          }\n"
            + "        ]\n"
            + "      }\n"
            + "    }\n"
            + "  ]\n"
            + "}";

    httpRequest.withBody(requestBody);
    httpRequest.withPath("domain");
    // When
    HttpResponse httpResponse = callback.handle(httpRequest);

    // Then
    ObjectNode expectedResponse =
        (ObjectNode) JacksonUtil.readAsTree(httpResponse.getBodyAsString());

    assertEquals(200, httpResponse.getStatusCode());
    assertNotNull(expectedResponse.get("id"));
    assertFalse(expectedResponse.get("id").asText().isEmpty());
    assertNotNull(expectedResponse.get("state"));
    assertEquals("acknowledged", expectedResponse.get("state").asText());

    JsonNode responseCache = CACHE.get("domain", expectedResponse.get("id").asText());

    assertNotNull(responseCache.get("id"));
    assertFalse(responseCache.get("id").asText().isEmpty());
    assertNotNull(responseCache.get("state"));
    assertEquals("acknowledged", responseCache.get("state").asText());
  }

  @Test
  void testPostServiceOrder_withTenantAdminInfo_returnWithIdAndStateAndCachedWithIsvId() {
    // Given
    String requestBody =
        "{\n"
            + "  \"category\": \"UNITY\",\n"
            + "  \"serviceOrderItem\": [\n"
            + "    {\n"
            + "      \"id\": \"100\",\n"
            + "      \"action\": \"add\",\n"
            + "      \"service\": {\n"
            + "        \"serviceType\": \"ucc.unity.tenant\",\n"
            + "        \"serviceCharacteristic\": [\n"
            + "          {\n"
            + "            \"name\": \"TenantInfo\",\n"
            + "            \"valueType\": \"object\",\n"
            + "            \"value\": {\n"
            + "              \"profile\": \"FULL_STACK_PREMIUM\",\n"
            + "              \"currency\": \"GBP\",\n"
            + "              \"name\": \"test 2\",\n"
            + "              \"mainNumber\": \"+441234567890\",\n"
            + "              \"@type\": \"UccTenantInfo\"\n"
            + "            }\n"
            + "          },\n"
            + "          {\n"
            + "            \"name\": \"TenantAdminInfo\",\n"
            + "            \"valueType\": \"object\",\n"
            + "            \"value\": {\n"
            + "              \"firstName\": \"Manpreet\",\n"
            + "              \"lastName\": \"Saggu\",\n"
            + "              \"email\": \"saggumanizer@gmail.com\",\n"
            + "              \"phoneNumber\": \"07421738251\",\n"
            + "              \"@type\": \"UccTenantAdminInfo\"\n"
            + "            }\n"
            + "          }\n"
            + "        ]\n"
            + "      }\n"
            + "    }\n"
            + "  ],\n"
            + "  \"@type\": \"ServiceOrder\"\n"
            + "}";

    httpRequest.withBody(requestBody);
    httpRequest.withPath("domain");

    // When
    HttpResponse httpResponse = callback.handle(httpRequest);

    // Then
    ObjectNode expectedResponse =
        (ObjectNode) JacksonUtil.readAsTree(httpResponse.getBodyAsString());

    assertEquals(200, httpResponse.getStatusCode());
    assertNotNull(expectedResponse.get("id"));
    assertFalse(expectedResponse.get("id").asText().isEmpty());
    assertNotNull(expectedResponse.get("state"));
    assertEquals("acknowledged", expectedResponse.get("state").asText());

    JsonNode responseTenantInfo = getTenantInfoValue(expectedResponse);
    assertNotNull(responseTenantInfo);
    assertNull(responseTenantInfo.get("isvId"));

    JsonNode responseCache = CACHE.get("domain", expectedResponse.get("id").asText());

    assertNotNull(responseCache.get("id"));
    assertFalse(responseCache.get("id").asText().isEmpty());
    assertNotNull(responseCache.get("state"));
    assertEquals("acknowledged", responseCache.get("state").asText());

    JsonNode cachedTenantInfo = getTenantInfoValue(responseCache);
    assertNotNull(cachedTenantInfo);
  }

  private ObjectNode getTenantInfoValue(JsonNode rootNode) {
    JsonNode serviceOrderItems = rootNode.path("serviceOrderItem");
    JsonNode serviceOrderItem = serviceOrderItems.get(0);

    JsonNode service = serviceOrderItem.path("service");
    JsonNode serviceCharacteristics = service.path("serviceCharacteristic");

    return StreamSupport.stream(serviceCharacteristics.spliterator(), false)
        .filter(sc -> "TenantInfo".equals(sc.path("name").asText()))
        .map(sc -> sc.path("value"))
        .filter(ObjectNode.class::isInstance)
        .map(ObjectNode.class::cast)
        .findFirst()
        .orElse(null);
  }
}
