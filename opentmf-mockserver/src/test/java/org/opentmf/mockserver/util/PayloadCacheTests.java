package org.opentmf.mockserver.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SortedMap;
import java.util.TimerTask;
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

  /**
   * putIfAbsent must insert when the key is absent, returning true. This is the fast path used
   * by every create callback (POST, PUT-create, batch collection PATCH) to close the
   * check-then-put race that used to raise IllegalArgumentException → 500 under concurrent
   * duplicate ids.
   */
  @Test
  void putIfAbsent_absentKey_insertsAndReturnsTrue() {
    String domain = "putifabsent-" + UUID.randomUUID();
    String id = UUID.randomUUID().toString();
    RequestContext ctx = contextOf(domain, id, "1");
    JsonNode value = entity(id, "1");

    assertTrue(CACHE.putIfAbsent(ctx, value));
    assertSame(value, CACHE.get(ctx));
  }

  /**
   * putIfAbsent must NOT overwrite an existing entry — it returns false and leaves the cache
   * untouched. This is what the create callbacks translate into their intended 400/409.
   */
  @Test
  void putIfAbsent_presentKey_returnsFalse_doesNotOverwrite() {
    String domain = "putifabsent-existing-" + UUID.randomUUID();
    String id = UUID.randomUUID().toString();
    RequestContext ctx = contextOf(domain, id, "1");
    JsonNode original = entity(id, "1");
    CACHE.put(ctx, original);

    JsonNode replacement = JacksonUtil.readAsTree("{\"id\":\"" + id + "\",\"marker\":\"new\"}");
    assertFalse(CACHE.putIfAbsent(ctx, replacement));

    // Original reference must still be what get() returns.
    assertSame(original, CACHE.get(ctx));
  }

  @Test
  void getInstance_isSingleton() {
    assertSame(PayloadCache.getInstance(), PayloadCache.getInstance());
  }

  @Test
  void put_duplicateKey_throwsIllegalArgumentException() {
    String domain = "duplicate-" + UUID.randomUUID();
    String id = UUID.randomUUID().toString();
    RequestContext ctx = contextOf(domain, id, "1");
    CACHE.put(ctx, entity(id, "1"));
    JsonNode duplicate = entity(id, "1");
    assertThrows(IllegalArgumentException.class, () -> CACHE.put(ctx, duplicate));
  }

  @Test
  void update_absentKey_throwsIllegalArgumentException() {
    String domain = "update-missing-" + UUID.randomUUID();
    String id = UUID.randomUUID().toString();
    RequestContext ctx = contextOf(domain, id, "1");
    CACHE.put(ctx, entity(id, "1"));
    RequestContext other = contextOf(domain, UUID.randomUUID().toString(), "1");
    JsonNode value = entity("x", "1");
    assertThrows(IllegalArgumentException.class, () -> CACHE.update(other, value));
  }

  @Test
  void update_presentKey_replacesValue() {
    String domain = "update-present-" + UUID.randomUUID();
    String id = UUID.randomUUID().toString();
    RequestContext ctx = contextOf(domain, id, "1");
    CACHE.put(ctx, entity(id, "1"));
    JsonNode newValue = JacksonUtil.readAsTree("{\"id\":\"" + id + "\",\"marker\":\"upd\"}");
    CACHE.update(ctx, newValue);
    assertSame(newValue, CACHE.get(ctx));
  }

  @Test
  void get_unknownDomain_returnsNull() {
    HttpRequest req = HttpRequest.request().withMethod("GET").withPath("/no-such");
    RequestContext ctx = RequestContext.initialize(req, false, entity("x", "1"));
    Id key = new Id();
    key.setId("x");
    key.setVersion("1");
    ctx.setId(key);
    assertNull(CACHE.get(ctx));
  }

  @Test
  void getLatestOf_unknownDomain_returnsNull() {
    RequestContext ctx = contextOf("no-domain-" + UUID.randomUUID(), "x", "1");
    assertNull(CACHE.getLatestOf(ctx));
  }

  @Test
  void getLatestOf_presentDomain_returnsLastVersionForId() {
    String domain = "latest-" + UUID.randomUUID();
    String id = UUID.randomUUID().toString();
    CACHE.put(contextOf(domain, id, "1"), entity(id, "1"));
    CACHE.put(contextOf(domain, id, "2"), entity(id, "2"));
    JsonNode latest = CACHE.getLatestOf(contextOf(domain, id, null));
    assertNotNull(latest);
    assertEquals("2", latest.get("version").asString());
  }

  @Test
  void getLatestVersion_unknownDomain_returnsNull() {
    Id key = new Id();
    key.setId("x");
    assertNull(CACHE.getLatestVersion("no-domain-" + UUID.randomUUID(), key));
  }

  @Test
  void getLatestVersion_presentDomain_returnsHighestVersion() {
    String domain = "latestver-" + UUID.randomUUID();
    String id = UUID.randomUUID().toString();
    RequestContext ctxV1 = contextOf(domain, id, "1");
    CACHE.put(ctxV1, entity(id, "1"));
    CACHE.put(contextOf(domain, id, "2"), entity(id, "2"));
    Id lookup = new Id();
    lookup.setId(id);
    // The cache is keyed by ctx.getDomain(), which the RequestContext prefixes from the request
    // path ("test-api/<domain>"). Look up with the same prefixed form.
    assertEquals("2", CACHE.getLatestVersion(ctxV1.getDomain(), lookup));
  }

  @Test
  void getLatestVersion_entryWithoutVersionField_returnsNull() {
    String domain = "noversion-" + UUID.randomUUID();
    String id = UUID.randomUUID().toString();
    RequestContext ctx = contextOf(domain, id, null);
    CACHE.put(ctx, JacksonUtil.readAsTree("{\"id\":\"" + id + "\"}"));
    Id lookup = new Id();
    lookup.setId(id);
    assertNull(CACHE.getLatestVersion(ctx.getDomain(), lookup));
  }

  @Test
  void touchByResource_nullDomain_isNoop() {
    assertDoesNotThrow(() -> CACHE.touchByResource(null, new Id()));
  }

  @Test
  void touchByResource_nullId_isNoop() {
    assertDoesNotThrow(() -> CACHE.touchByResource("some-domain", null));
  }

  @Test
  void touchByResource_unknownDomain_isNoop() {
    Id key = new Id();
    key.setId("x");
    String unknown = "no-such-" + UUID.randomUUID();
    assertDoesNotThrow(() -> CACHE.touchByResource(unknown, key));
  }

  @Test
  void touchByResource_missingId_isNoop() {
    String domain = "touch-missing-" + UUID.randomUUID();
    String id = UUID.randomUUID().toString();
    CACHE.put(contextOf(domain, id, "1"), entity(id, "1"));
    Id other = new Id();
    other.setId("other-id");
    assertDoesNotThrow(() -> CACHE.touchByResource(domain, other));
  }

  @Test
  void touchByResource_presentEntry_updatesTimestampWithoutError() {
    String domain = "touch-ok-" + UUID.randomUUID();
    String id = UUID.randomUUID().toString();
    RequestContext ctx = contextOf(domain, id, "1");
    CACHE.put(ctx, entity(id, "1"));
    assertDoesNotThrow(() -> CACHE.touchByResource(domain, ctx.getId()));
    // Entry must still be present after the touch.
    assertNotNull(CACHE.get(ctx));
  }

  @Test
  void clear_removesEntryFromDomain() {
    String domain = "clear-" + UUID.randomUUID();
    String id = UUID.randomUUID().toString();
    RequestContext ctx = contextOf(domain, id, "1");
    CACHE.put(ctx, entity(id, "1"));
    CACHE.clear(ctx);
    assertNull(CACHE.get(ctx));
  }

  /**
   * The private static {@code CacheEvictTimer} is a {@link TimerTask} that fires on the cache TTL.
   * Reflectively construct and {@code run()} it: the task delegates to
   * {@code PayloadCache.getInstance().evictOldItems()}, which is idempotent when nothing is expired
   * (cache TTL is 2h in test JVM). Provides coverage of the class body without wall-clock waits.
   */
  @Test
  void cacheEvictTimer_run_invokesEvictOldItemsWithoutError() throws Exception {
    Class<?> timerClass =
        Class.forName("org.opentmf.mockserver.util.PayloadCache$CacheEvictTimer");
    var ctor = timerClass.getDeclaredConstructor();
    ctor.setAccessible(true);
    TimerTask task = (TimerTask) ctor.newInstance();
    // Populate one entry so evictOldItems has domains to iterate; it will not be expired.
    String domain = "evict-timer-" + UUID.randomUUID();
    String id = UUID.randomUUID().toString();
    RequestContext ctx = contextOf(domain, id, "1");
    CACHE.put(ctx, entity(id, "1"));

    task.run();

    // Entry must still be there — TTL is well above test duration.
    assertNotNull(CACHE.get(ctx));
  }
}
