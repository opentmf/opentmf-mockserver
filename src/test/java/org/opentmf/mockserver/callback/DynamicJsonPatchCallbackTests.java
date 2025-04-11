package org.opentmf.mockserver.callback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import org.opentmf.mockserver.util.JacksonUtil;
import org.opentmf.mockserver.util.PayloadCache;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

class DynamicJsonPatchCallbackTests {
  private static final PayloadCache CACHE = PayloadCache.getInstance();
  private DynamicJsonPatchCallback callback;
  private HttpRequest httpRequest;

  @BeforeEach
  void setUp() {
    callback = new DynamicJsonPatchCallback();
    httpRequest = new HttpRequest();
  }

  @Test
  void shouldApplyJsonPatch() {
    // Given
    String id = UUID.randomUUID().toString();
    String domain = RandomStringUtils.randomAlphabetic(5);
    addDataToCache(domain, id);
    String requestBody =
        "[\n"
            + "    { \"op\": \"add\", \"path\": \"/contactMedium/0/characteristic/street1\", \"value\": \"New Street\" },\n"
            + "    { \"op\": \"replace\", \"path\": \"/contactMedium/1/characteristic/country\", \"value\": \"US\" }\n"
            + "]";

    httpRequest.withPath(domain + "/" + id).withBody(requestBody);

    // When
    HttpResponse httpResponse = callback.handle(httpRequest);

    // Then
    assertEquals(200, httpResponse.getStatusCode());

    JsonNode updatedServiceOrderJson = CACHE.get(domain, id);
    assertNotNull(updatedServiceOrderJson);

    JsonNode contactMediumNode = updatedServiceOrderJson.path("contactMedium");
    assertNotNull(contactMediumNode);
    assertEquals(3, contactMediumNode.size());

    JsonNode firstContactMediumNode = contactMediumNode.get(0);
    assertNotNull(firstContactMediumNode);
    assertEquals(
        "New Street", firstContactMediumNode.path("characteristic").path("street1").asText());

    JsonNode secondContactMediumNode = contactMediumNode.get(1);
    assertNotNull(secondContactMediumNode);
    assertEquals("US", secondContactMediumNode.path("characteristic").path("country").asText());
  }

  @Test
  void testApplyPatch_withNonExistId() {
    // Given
    String id = UUID.randomUUID().toString();
    String domain = RandomStringUtils.randomAlphabetic(5);
    addDataToCache(domain, "nonExistId");
    String requestBody =
        "[\n"
            + "    { \"op\": \"add\", \"path\": \"/contactMedium/0/characteristic/street1\", \"value\": \"New Street\" },\n"
            + "    { \"op\": \"replace\", \"path\": \"/contactMedium/1/characteristic/country\", \"value\": \"US\" }\n"
            + "]";

    httpRequest.withPath(domain + "/" + id).withBody(requestBody);

    // When
    HttpResponse httpResponse = callback.handle(httpRequest);

    // Then
    assertEquals(404, httpResponse.getStatusCode());
  }

  private void addDataToCache(String domain, String id) {
    HttpRequest request =
        new HttpRequest()
            .withPath("/" + domain)
            .withBody(JacksonUtil.writeAsString(getInitialJson(id)));

    DynamicPostCallback dynamicPostCallback = new DynamicPostCallback();
    HttpResponse httpResponse = dynamicPostCallback.handle(request);
    assertEquals(200, httpResponse.getStatusCode());
  }

  private JsonNode getInitialJson(String serviceOrderId) {
    String payload =  "{\n"
        + "    \"id\": \""
        + serviceOrderId
        + "\",\n"
        + "    \"name\": \"{{$randomFullName}}\",\n"
        + "    \"engagedParty\": {\n"
        + "        \"@referredType\": \"Organization\",\n"
        + "        \"href\": \"/tmf-api/party/v4/organization/{{organizationId}}\",\n"
        + "        \"id\": \"{{organizationId}}\",\n"
        + "        \"name\": \"{{organizationName}}\"\n"
        + "    },\n"
        + "    \"characteristic\": [\n"
        + "        {\n"
        + "            \"name\": \"NOMINATED_PARTY_ID\",\n"
        + "            \"value\": \"123\"\n"
        + "        },\n"
        + "        {\n"
        + "            \"name\": \"SUPPORT_SYSTEM_ID\",\n"
        + "            \"value\": \"456\"\n"
        + "        }\n"
        + "    ],\n"
        + "    \"relatedParty\": [\n"
        + "        {\n"
        + "            \"id\": \"VFUK\",\n"
        + "            \"href\": \"/tmf-api/party/v4/organization/VFUK\",\n"
        + "            \"role\": \"operator\",\n"
        + "            \"name\": \"Vodafone UK2\",\n"
        + "            \"@referredType\": \"Organization\"\n"
        + "        }\n"
        + "    ],\n"
        + "    \"contactMedium\": [\n"
        + "        {\n"
        + "            \"mediumType\": \"site\",\n"
        + "            \"preferred\": false,\n"
        + "            \"characteristic\": {\n"
        + "                \"contactType\": \"HQ\",\n"
        + "                \"country\": \"GB\",\n"
        + "                \"postCode\": \"RG\",\n"
        + "                \"street1\": \"23\"\n"
        + "            }\n"
        + "        },\n"
        + "        {\n"
        + "            \"mediumType\": \"site\",\n"
        + "            \"preferred\": false,\n"
        + "            \"characteristic\": {\n"
        + "                \"contactType\": \"Dublin\",\n"
        + "                \"country\": \"IE\",\n"
        + "                \"postCode\": \"RF\",\n"
        + "                \"street1\": \"12\"\n"
        + "            }\n"
        + "        },\n"
        + "        {\n"
        + "            \"mediumType\": \"site\",\n"
        + "            \"preferred\": false,\n"
        + "            \"characteristic\": {\n"
        + "                \"contactType\": \"Belfast\",\n"
        + "                \"country\": \"IE\",\n"
        + "                \"postCode\": \"DS\",\n"
        + "                \"street1\": \"43\"\n"
        + "            }\n"
        + "        }\n"
        + "    ],\n"
        + "    \"account\": [\n"
        + "        {\n"
        + "            \"id\": \"{{billingAccountId}}\",\n"
        + "            \"name\": \"{{billingAccountName}}\",\n"
        + "            \"href\": \"/tmf-api/accountManagement/v4/billingAccount/{{billingAccountId}}\",\n"
        + "            \"description\": \"\",\n"
        + "            \"@referredType\": \"BillingAccount\"\n"
        + "        }\n"
        + "    ]\n"
        + "}";

    return JacksonUtil.readAsTree(payload);
  }
}
