package org.opentmf.mockserver.callback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.opentmf.mockserver.util.JacksonUtil;
import org.opentmf.mockserver.util.PayloadCache;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

class DynamicDeleteCallbackTests {
  private static final PayloadCache CACHE = PayloadCache.getInstance();
  private DynamicDeleteCallback callback;
  private HttpRequest httpRequest;

  @BeforeEach
  void setUp() {
    callback = new DynamicDeleteCallback();
    httpRequest = new HttpRequest();
  }

  @Test
  void shouldDeleteFromCache() {
    // Given
    String id = UUID.randomUUID().toString();
    String domain = RandomStringUtils.randomAlphabetic(5);
    addDataToCache(domain, id, "created");

    httpRequest.withPath(domain + "/" + id);
    // When
    HttpResponse httpResponse = callback.handle(httpRequest);

    // Then
    assertEquals(204, httpResponse.getStatusCode());
    assertNull(CACHE.get(domain, id));
  }

  @Test
  void shouldReturnNotFoundIfNotInCache() {
    // Given
    String id = "456";
    String domain = RandomStringUtils.randomAlphabetic(5);
    httpRequest.withPath(domain + "/" + id);

    // When
    HttpResponse httpResponse = callback.handle(httpRequest);

    // Then
    assertEquals(404, httpResponse.getStatusCode());
  }

  private void addDataToCache(String domain, String id, String status) {
    ObjectNode node = JacksonUtil.createObjectNode();
    node.put("status", status);
    node.put("id", id);

    HttpRequest request =
        new HttpRequest().withPath("/" + domain).withBody(JacksonUtil.writeAsString(node));

    DynamicPostCallback dynamicPostCallback = new DynamicPostCallback();
    HttpResponse httpResponse = dynamicPostCallback.handle(request);
    assertEquals(200, httpResponse.getStatusCode());
  }
}
