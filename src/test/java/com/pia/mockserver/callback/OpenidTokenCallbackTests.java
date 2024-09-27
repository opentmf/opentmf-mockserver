package com.pia.mockserver.callback;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockserver.model.HttpRequest.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pia.mockserver.util.JacksonUtil;
import org.apache.http.HttpHeaders;
import org.junit.jupiter.api.Test;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

class OpenidTokenCallbackTests {

  @Test
  void testShTokenCallback() {
    String scope = "PRODUCT_ORDER_CREATE";
    HttpRequest request =
        request()
            .withMethod("POST")
            .withPath("solutionHubOAuth2ClientCredentialsGrant/v1/token")
            .withBody(
                "grant_type=client_credentials&client_id=XXXXXX&client_secret=XXXXXXXXsgfdss&scope="
                    + scope)
            .withHeader(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
            .withHeader(HttpHeaders.ACCEPT, "application/json");

    OpenidTokenCallback tokenCallback = new OpenidTokenCallback();
    HttpResponse httpResponse = tokenCallback.handle(request);

    ObjectNode expectedResponse =
        (ObjectNode) JacksonUtil.readAsTree(httpResponse.getBodyAsString());

    assertEquals(200, httpResponse.getStatusCode());
    assertNotNull(expectedResponse.get("access_token"));
    assertNotNull(expectedResponse.get("refresh_token"));
    assertNotNull(expectedResponse.get("id_token"));
    assertNotNull(expectedResponse.get("token_type"));
    assertNotNull(expectedResponse.get("expires_in"));
    assertEquals(scope, expectedResponse.get("scope").asText());
  }
}
