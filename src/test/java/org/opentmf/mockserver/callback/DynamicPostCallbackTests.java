package org.opentmf.mockserver.callback;

import static org.apache.commons.lang3.RandomStringUtils.randomNumeric;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.opentmf.mockserver.model.TmfConstants.VERSION;
import static org.opentmf.mockserver.model.TmfStatePath.CATALOG;
import static org.opentmf.mockserver.util.Constants.ADDITIONAL_FIELDS;
import static org.opentmf.mockserver.util.Constants.CACHE_DURATION_MILLIS;
import static org.opentmf.mockserver.util.Constants.THREE_SECONDS;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.SortedMap;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.opentmf.mockserver.model.Id;
import org.opentmf.mockserver.model.RequestContext;
import org.opentmf.mockserver.util.JacksonUtil;
import org.opentmf.mockserver.util.PayloadCache;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith(SystemStubsExtension.class)
class DynamicPostCallbackTests {

  private static final PayloadCache CACHE = PayloadCache.getInstance();
  private DynamicPostCallback callback;

  private HttpRequest httpRequest;

  @SystemStub
  private static final EnvironmentVariables TEST_ENV_VARIABLES =
      new EnvironmentVariables(
          CACHE_DURATION_MILLIS, THREE_SECONDS,
          ADDITIONAL_FIELDS, "project"
      );


  @BeforeAll
  static void beforeAll() {
    System.setProperty(CACHE_DURATION_MILLIS, THREE_SECONDS);
  }

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
    RequestContext ctx = RequestContext.initialize(httpRequest, false, JacksonUtil.readAsTree(requestBody));

    // When
    HttpResponse httpResponse = callback.handle(httpRequest);

    // Then
    ObjectNode expectedResponse =
        (ObjectNode) JacksonUtil.readAsTree(httpResponse.getBodyAsString());
    setIdFromResponse(expectedResponse, ctx);

    assertEquals(200, httpResponse.getStatusCode());
    assertNotNull(expectedResponse.get("id"));
    assertFalse(expectedResponse.get("id").asText().isEmpty());
    assertNotNull(expectedResponse.get("state"));
    assertEquals("acknowledged", expectedResponse.get("state").asText());

    JsonNode responseCache = CACHE.get(ctx);

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
            + "              \"firstName\": \"James\",\n"
            + "              \"lastName\": \"Bond\",\n"
            + "              \"email\": \"jamesbond007@gmail.com\",\n"
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

    RequestContext ctx = RequestContext.initialize(httpRequest, false, JacksonUtil.readAsTree(requestBody));

    // When
    HttpResponse httpResponse = callback.handle(httpRequest);

    // Then
    ObjectNode expectedResponse =
        (ObjectNode) JacksonUtil.readAsTree(httpResponse.getBodyAsString());

    setIdFromResponse(expectedResponse, ctx);

    assertEquals(200, httpResponse.getStatusCode());
    assertNotNull(expectedResponse.get("id"));
    assertFalse(expectedResponse.get("id").asText().isEmpty());
    assertNotNull(expectedResponse.get("state"));
    assertEquals("acknowledged", expectedResponse.get("state").asText());

    JsonNode responseTenantInfo = getTenantInfoValue(expectedResponse);
    assertNotNull(responseTenantInfo);
    assertNull(responseTenantInfo.get("isvId"));

    JsonNode responseCache = CACHE.get(ctx);

    assertNotNull(responseCache.get("id"));
    assertFalse(responseCache.get("id").asText().isEmpty());
    assertNotNull(responseCache.get("state"));
    assertEquals("acknowledged", responseCache.get("state").asText());

    JsonNode cachedTenantInfo = getTenantInfoValue(responseCache);
    assertNotNull(cachedTenantInfo);
  }

  @Test
  void testPost_withAllDifferentVersionPatterns_parsesAndCachesCorrectly() {
    String path = CATALOG.getPath();
    SortedMap<Id, JsonNode> before = CACHE.getAll(path);
    post(new HttpRequest().withPath("/" + path), null, null);
    post(new HttpRequest().withPath("/" + path), randomNumeric(10), null);
    post(new HttpRequest().withPath("/" + path), randomNumeric(10), "1.0");
    post(new HttpRequest().withPath("/" + path)
        .withQueryStringParameter("version", "1.0"), randomNumeric(10), null);
    post(new HttpRequest().withPath("/" + path + "?version=1.0"), randomNumeric(10), null);
    post(new HttpRequest().withPath("/" + path + ":(version=1.0)"), randomNumeric(10), null);
    Assertions.assertEquals(before.size() + 6, CACHE.getAll(path).size());
  }

  void post(HttpRequest httpRequest, String id, String version) {
    httpRequest.withBody(payload(id, version));
    HttpResponse handle = callback.handle(httpRequest);
    assertEquals(200, handle.getStatusCode());
    ObjectNode result = (ObjectNode) JacksonUtil.readAsTree(handle.getBodyAsString());
    System.out.println(JacksonUtil.writeAsString(result));
    Assertions.assertNotNull(result.get("id"));
    Assertions.assertNotNull(result.get("version"));
    Assertions.assertNotNull(result.get("project"));
  }

  private String payload(String id, String version) {
    StringBuilder buf = new StringBuilder();
    buf.append('{');
    int n = 0;
    if (id != null) {
      n++;
      buf.append("\"id\":\"").append(id).append("\"");
    }
    if (version != null) {
      if (n > 0) {
        buf.append(',');
      }
      buf.append("\"version\":\"").append(version).append("\"");
    }
    buf.append('}');
    return buf.toString();
  }

  private static void setIdFromResponse(ObjectNode expectedResponse, RequestContext ctx) {
    Id id = new Id();
    id.setId(expectedResponse.get("id").asText());
    if (expectedResponse.has(VERSION)) {
      id.setVersion(expectedResponse.get(VERSION).asText());
    }
    ctx.setId(id);
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
