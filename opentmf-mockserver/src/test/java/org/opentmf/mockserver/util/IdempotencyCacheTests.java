package org.opentmf.mockserver.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.TimerTask;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.opentmf.mockserver.model.Id;

class IdempotencyCacheTests {

  private static final IdempotencyCache CACHE = IdempotencyCache.getInstance();

  private static Id id(String s) {
    Id key = new Id();
    key.setId(s);
    return key;
  }

  private static IdempotencyCache.Record recordFor(String domain, String resourceId) {
    return new IdempotencyCache.Record(
        "POST", "/api/x", 201, "application/json", "{}", domain, id(resourceId), System.currentTimeMillis());
  }

  @Test
  void getInstance_isSingleton() {
    assertSame(IdempotencyCache.getInstance(), IdempotencyCache.getInstance());
  }

  @Test
  void putThenGet_returnsSameRecord() {
    String key = "k-" + UUID.randomUUID();
    IdempotencyCache.Record rec = recordFor("d1", "r1");
    CACHE.put(key, rec);
    assertSame(rec, CACHE.get(key));
  }

  @Test
  void get_unknownKey_returnsNull() {
    assertNull(CACHE.get("no-such-key-" + UUID.randomUUID()));
  }

  @Test
  void record_getters_returnConstructorValues() {
    IdempotencyCache.Record rec =
        new IdempotencyCache.Record(
            "PATCH",
            "/api/y/42",
            200,
            "application/merge-patch+json",
            "{\"upd\":true}",
            "y",
            id("42"),
            1234567890L);
    assertEquals("PATCH", rec.getMethod());
    assertEquals("/api/y/42", rec.getPath());
    assertEquals(200, rec.getStatusCode());
    assertEquals("application/merge-patch+json", rec.getContentType());
    assertEquals("{\"upd\":true}", rec.getBody());
    assertEquals("y", rec.getDomain());
    assertEquals("42", rec.getResourceId().getId());
    assertEquals(1234567890L, rec.getCompletedAt());
  }

  @Test
  void evictForResource_removesMatchingRecords_leavesOthers() {
    String suffix = "-" + UUID.randomUUID();
    String matchKey = "match" + suffix;
    String otherKey = "other" + suffix;
    CACHE.put(matchKey, recordFor("d-evict" + suffix, "r-evict" + suffix));
    CACHE.put(otherKey, recordFor("d-keep" + suffix, "r-keep" + suffix));

    CACHE.evictForResource("d-evict" + suffix, id("r-evict" + suffix));

    assertNull(CACHE.get(matchKey));
    assertNotNull(CACHE.get(otherKey));
  }

  @Test
  void evictForResource_nothingMatches_leavesRecordsUntouched() {
    String key = "keep-" + UUID.randomUUID();
    CACHE.put(key, recordFor("d-x", "r-x"));

    CACHE.evictForResource("d-not-there", id("r-x"));
    assertNotNull(CACHE.get(key));

    CACHE.evictForResource("d-x", id("r-not-there"));
    assertNotNull(CACHE.get(key));
  }

  @Test
  void clear_dropsAllRecords() {
    CACHE.put("clear-1", recordFor("d", "r1"));
    CACHE.put("clear-2", recordFor("d", "r2"));
    CACHE.clear();
    assertNull(CACHE.get("clear-1"));
    assertNull(CACHE.get("clear-2"));
  }

  /**
   * The inner {@code EvictTimer} class purges records whose {@code completedAt} is older than TTL.
   * Reflectively instantiate it (non-static inner, so it needs the enclosing instance) and drive
   * its {@code run()} directly: put a record with a very old {@code completedAt} and one fresh
   * one, then verify only the old one is dropped. Avoids wall-clock waits.
   */
  @Test
  void evictTimer_run_purgesExpiredRecords_keepsFresh() throws Exception {
    CACHE.clear();
    String freshKey = "fresh-" + UUID.randomUUID();
    String staleKey = "stale-" + UUID.randomUUID();
    CACHE.put(freshKey, recordFor("d", "r-fresh"));
    CACHE.put(
        staleKey,
        new IdempotencyCache.Record(
            "POST", "/api/x", 201, "application/json", "{}", "d", id("r-stale"), 0L));

    Class<?> timerClass = Class.forName("org.opentmf.mockserver.util.IdempotencyCache$EvictTimer");
    var ctor = timerClass.getDeclaredConstructor(IdempotencyCache.class);
    ctor.setAccessible(true);
    TimerTask task = (TimerTask) ctor.newInstance(CACHE);

    task.run();

    assertNull(CACHE.get(staleKey), "record with completedAt=0 must be evicted");
    assertNotNull(CACHE.get(freshKey), "recently completed record must survive");
  }
}
