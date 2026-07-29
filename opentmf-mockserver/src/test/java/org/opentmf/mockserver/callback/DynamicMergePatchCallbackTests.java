package org.opentmf.mockserver.callback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith(SystemStubsExtension.class)
class DynamicMergePatchCallbackTests {
  private static final PayloadCache CACHE = PayloadCache.getInstance();
  private DynamicMergePatchCallback callback;
  private HttpRequest httpRequest;

  @BeforeEach
  void setUp() {
    callback = new DynamicMergePatchCallback();
    httpRequest = new HttpRequest();
  }

  @Test
  void shouldReturnUpdatedServiceOrder() {
    // Given
    String id = TSID.Factory.getTsid().toString();
    String domain = "mockserver";
    String requestBody =
        """
        {
            "relatedParty": [
                {
                    "id": "customerId",
                    "href": "/tmf-api/customerManagement/v4/customer/customerId",
                    "name": "customerName",
                    "role": "customer",
                    "@referredType": "Customer"
                },
                {
                    "id": "VFUK",
                    "href": "/tmf-api/party/v4/organization/VFUK",
                    "name": "Vodafone UK2",
                    "role": "operator",
                    "@referredType": "Organization"
                }
            ]
        }""";

    httpRequest.withPath(domain + "/" + id).withBody(requestBody);
    RequestContext ctx =
        RequestContext.initialize(httpRequest, true, JacksonUtil.readAsTree(requestBody));

    CACHE.put(ctx, getInitialJson(id));

    // When
    HttpResponse httpResponse = callback.handle(httpRequest);

    // Then
    assertEquals(200, httpResponse.getStatusCode());

    JsonNode updatedServiceOrderJson = CACHE.get(ctx);
    assertNotNull(updatedServiceOrderJson);

    JsonNode relatedPartyNode = updatedServiceOrderJson.path("relatedParty");
    assertNotNull(relatedPartyNode);
    assertEquals(2, relatedPartyNode.size());

    JsonNode firstRelatedPartyNode = relatedPartyNode.get(0);
    assertNotNull(firstRelatedPartyNode);

    assertEquals("customerId", firstRelatedPartyNode.path("id").asString());
    assertEquals(
        "/tmf-api/customerManagement/v4/customer/customerId",
        firstRelatedPartyNode.path("href").asString());
    assertEquals("customerName", firstRelatedPartyNode.path("name").asString());
    assertEquals("customer", firstRelatedPartyNode.path("role").asString());
    assertEquals("Customer", firstRelatedPartyNode.path("@referredType").asString());

    JsonNode secondRelatedPartyNode = relatedPartyNode.get(1);
    assertNotNull(secondRelatedPartyNode);

    assertEquals("VFUK", secondRelatedPartyNode.path("id").asString());
    assertEquals(
        "/tmf-api/party/v4/organization/VFUK", secondRelatedPartyNode.path("href").asString());
    assertEquals("Vodafone UK2", secondRelatedPartyNode.path("name").asString());
    assertEquals("operator", secondRelatedPartyNode.path("role").asString());
    assertEquals("Organization", secondRelatedPartyNode.path("@referredType").asString());
  }

  @Test
  void testApplyPatch_withNonExistId() {
    // Given
    String id = TSID.Factory.getTsid().toString();
    String domain = "mockserver";
    String requestBody =
        """
        {
            "relatedParty": [
                {
                    "id": "customerId",
                    "href": "/tmf-api/customerManagement/v4/customer/customerId",
                    "name": "customerName",
                    "role": "customer",
                    "@referredType": "Customer"
                },
                {
                    "id": "VFUK",
                    "href": "/tmf-api/party/v4/organization/VFUK",
                    "name": "Vodafone UK2",
                    "role": "operator",
                    "@referredType": "Organization"
                }
            ]
        }""";

    httpRequest.withPath(domain + "/" + id).withBody(requestBody);
    // When
    HttpResponse httpResponse = callback.handle(httpRequest);

    // Then
    assertEquals(404, httpResponse.getStatusCode());
  }

  private JsonNode getInitialJson(String serviceOrderId) {
    String payload =
        "{\n"
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
