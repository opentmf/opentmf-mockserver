package org.opentmf.mockserver.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SortedMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockserver.model.HttpRequest;
import org.opentmf.mockserver.model.Id;
import org.opentmf.mockserver.model.RequestContext;
import tools.jackson.databind.JsonNode;

class PayloadCacheTests {

  private static final PayloadCache CACHE = PayloadCache.getInstance();

  private static RequestContext contextOf(String domain, String id, String version) {
    HttpRequest request =
        HttpRequest.request().withMethod("POST").withPath("/test-api/" + domain);
    RequestContext ctx = RequestContext.initialize(request, false, entity(id, version));
    Id key = new Id();
    key.setId(id);
    key.setVersion(version);
    ctx.setId(key);
    return ctx;
  }

  private static JsonNode entity(String id, String version) {
    return JacksonUtil.readAsTree("{\"id\":\"" + id + "\",\"version\":\"" + version + "\"}");
  }

  /**
   * getAll() must return a snapshot, not the live internal map. The list-GET callback iterates
   * the result outside the cache lock while writers keep inserting; with the live map that
   * iteration intermittently missed entries that were already 201-created (observed as ~1% 404s
   * on filtered list GETs under parallel load, 2026-07-07). A snapshot is immune by definition:
   * a put() AFTER the snapshot was taken must not appear in it.
   */
  @Test
  void getAll_returnsSnapshot_laterPutsDoNotAppear() {
    String domain = "snapshot-" + UUID.randomUUID();
    String firstId = UUID.randomUUID().toString();
    RequestContext first = contextOf(domain, firstId, "1");
    CACHE.put(first, entity(firstId, "1"));

    SortedMap<Id, JsonNode> snapshot = CACHE.getAll(first.getDomain());
    assertEquals(1, snapshot.size());

    String secondId = UUID.randomUUID().toString();
    CACHE.put(contextOf(domain, secondId, "1"), entity(secondId, "1"));

    // The live map would now show 2 — the snapshot must still show 1.
    assertEquals(1, snapshot.size());
    assertEquals(2, CACHE.getAll(first.getDomain()).size());
  }

  /** Mutating the returned map must not corrupt the cache (it is a copy, not a view). */
  @Test
  void getAll_returnedMapMutation_doesNotAffectCache() {
    String domain = "mutation-" + UUID.randomUUID();
    String id = UUID.randomUUID().toString();
    RequestContext ctx = contextOf(domain, id, "1");
    CACHE.put(ctx, entity(id, "1"));

    CACHE.getAll(ctx.getDomain()).clear();

    assertEquals(1, CACHE.getAll(ctx.getDomain()).size());
    assertNotNull(CACHE.get(ctx));
  }

  /** Unknown domains keep returning an empty map (pre-fix contract, unchanged). */
  @Test
  void getAll_unknownDomain_returnsEmpty() {
    assertTrue(CACHE.getAll("no-such-domain-" + UUID.randomUUID()).isEmpty());
  }
}
