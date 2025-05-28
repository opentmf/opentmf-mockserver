package org.opentmf.mockserver.callback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.opentmf.mockserver.util.Constants.ADDITIONAL_FIELDS;
import static org.opentmf.mockserver.util.Constants.CACHE_DURATION_MILLIS;
import static org.opentmf.mockserver.util.Constants.THREE_SECONDS;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.opentmf.mockserver.model.RequestContext;
import org.opentmf.mockserver.util.JacksonUtil;
import org.opentmf.mockserver.util.PayloadCache;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith(SystemStubsExtension.class)
class DynamicDeleteCallbackTests {
  private static final PayloadCache CACHE = PayloadCache.getInstance();
  private DynamicDeleteCallback callback;
  private HttpRequest httpRequest;

  @SystemStub
  private static final EnvironmentVariables TEST_ENV_VARIABLES =
      new EnvironmentVariables(
          CACHE_DURATION_MILLIS, THREE_SECONDS,
          ADDITIONAL_FIELDS, "project"
      );

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
    RequestContext ctx = RequestContext.initialize(httpRequest, true, null);
    // When
    HttpResponse httpResponse = callback.handle(httpRequest);

    // Then
    assertEquals(204, httpResponse.getStatusCode());
    assertNull(CACHE.get(ctx));
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
