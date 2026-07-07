package org.opentmf.mockserver.util;

import static org.opentmf.mockserver.model.TmfConstants.VERSION;
import static org.opentmf.mockserver.util.Constants.CACHE_DURATION_MILLIS;
import static org.opentmf.mockserver.util.Constants.TWO_HOURS;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import org.opentmf.mockserver.model.Id;
import org.opentmf.mockserver.model.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

/**
 * Utility class for caching payloads with time-based eviction. This class provides methods to put,
 * update, get, and clear cache entries. Cache entries are stored as maps with domain and key
 * identifiers. Cache eviction is performed based on a specified time-to-live (TTL) for entries.
 *
 * @author Gokhan Demir
 */
public class PayloadCache {

  private static final Logger LOG = LoggerFactory.getLogger(PayloadCache.class);
  private static final String NO_CACHE_ENTRY_FOUND_FOR_DOMAIN =
      "No cache entry found for domain = \"{}\"";
  private static final String START_EVICTING_OLD_CACHE_ITEMS = "Start evicting old cache items.";
  private static final String DOMAIN_WITH = "domain = \"{}\" with [{}]";

  private final Map<String, TreeMap<Id, JsonNode>> dataCache = new LinkedHashMap<>();
  private final Map<String, TreeMap<Id, Long>> timeCache = new HashMap<>();

  private final long timeToLive;

  private PayloadCache(long timeToLive) {
    this.timeToLive = timeToLive;
    LOG.info("Cache initialized to expire in {}", DurationUtil.formatDuration(timeToLive));
    new Timer(true).scheduleAtFixedRate(new CacheEvictTimer(), timeToLive, timeToLive);
  }

  private static volatile PayloadCache instance = null;

  /**
   * Returns the singleton instance of PayloadCache with default TTL.
   *
   * @return The singleton instance of PayloadCache.
   */
  public static PayloadCache getInstance() {
    if (instance == null) {
      synchronized (PayloadCache.class) {
        if (instance == null) {
          String cacheDurationMillis = System.getenv(CACHE_DURATION_MILLIS);
          long milliseconds =
              Long.parseLong(cacheDurationMillis == null ? TWO_HOURS : cacheDurationMillis);
          instance = new PayloadCache(milliseconds);
        }
      }
    }
    return instance;
  }

  // TimerTask for cache eviction
  private static class CacheEvictTimer extends TimerTask {
    @Override
    public void run() {
      LOG.info(START_EVICTING_OLD_CACHE_ITEMS);
      getInstance().evictOldItems();
    }
  }

  public synchronized void put(RequestContext ctx, JsonNode value) {
    dataCache.putIfAbsent(ctx.getDomain(), new TreeMap<>());
    timeCache.putIfAbsent(ctx.getDomain(), new TreeMap<>());

    if (dataCache.get(ctx.getDomain()).containsKey(ctx.getId())) {
      throw new IllegalArgumentException(
          "Key: [" + ctx.getId() + "] already exists in cache for domain ");
    }

    dataCache.get(ctx.getDomain()).put(ctx.getId(), value);
    timeCache.get(ctx.getDomain()).put(ctx.getId(), System.currentTimeMillis());
    LOG.info("Cache entry for " + DOMAIN_WITH + " added", ctx.getDomain(), ctx.getId());
  }

  public synchronized void update(RequestContext ctx, JsonNode value) {
    if (!dataCache.get(ctx.getDomain()).containsKey(ctx.getId())) {
      throw new IllegalArgumentException();
    }
    touch(ctx);
    dataCache.get(ctx.getDomain()).put(ctx.getId(), value);
  }

  public synchronized void touch(RequestContext ctx) {
    Id lowerBound = new Id();
    lowerBound.setId(ctx.getId().getId());
    // null sorts before everything in Id.compareTo, so use null as the lower bound
    // to capture both non-versioned (version=null) and all versioned entries
    lowerBound.setVersion(null);
    timeCache
        .get(ctx.getDomain())
        .subMap(lowerBound, true, allOf(lowerBound), true)
        .replaceAll((k, v) -> System.currentTimeMillis());
  }

  /**
   * Resets the TTL counter for the exact {@code (domain, id)} entry if it is still cached.
   * No-op when the domain or entry is absent. Used by idempotency-key replay to "touch" the
   * resource a prior request created or updated.
   */
  public synchronized void touchByResource(String domain, Id id) {
    if (domain == null || id == null) {
      return;
    }
    TreeMap<Id, Long> domainTimes = timeCache.get(domain);
    if (domainTimes == null || !domainTimes.containsKey(id)) {
      return;
    }
    domainTimes.put(id, System.currentTimeMillis());
  }

  public synchronized JsonNode getLatestOf(RequestContext ctx) {
    if (dataCache.get(ctx.getDomain()) == null) {
      LOG.info(NO_CACHE_ENTRY_FOUND_FOR_DOMAIN, ctx.getDomain());
      return null;
    }
    TreeMap<Id, JsonNode> map = dataCache.get(ctx.getDomain());
    Map.Entry<Id, JsonNode> lastEntry =
        map.subMap(ctx.getId(), true, allOf(ctx.getId()), true).lastEntry();
    return lastEntry != null ? lastEntry.getValue() : null;
  }

  public synchronized String getLatestVersion(String domain, Id key) {
    if (dataCache.get(domain) == null) {
      LOG.info(NO_CACHE_ENTRY_FOUND_FOR_DOMAIN, domain);
      return null;
    }
    TreeMap<Id, JsonNode> map = dataCache.get(domain);
    Map.Entry<Id, JsonNode> lastEntry = map.subMap(key, true, allOf(key), true).lastEntry();
    if (lastEntry == null || lastEntry.getValue() == null || !lastEntry.getValue().has(VERSION)) {
      return null;
    }
    return lastEntry.getValue().get(VERSION).asText();
  }

  public synchronized JsonNode get(RequestContext ctx) {
    LOG.info("Getting cache entry for " + DOMAIN_WITH, ctx.getDomain(), ctx.getId());

    if (dataCache.get(ctx.getDomain()) == null) {
      LOG.info(NO_CACHE_ENTRY_FOUND_FOR_DOMAIN, ctx.getDomain());
      return null;
    }
    return dataCache.get(ctx.getDomain()).get(ctx.getId());
  }

  /**
   * Returns a <b>snapshot</b> of the domain's entries, taken under the cache lock. Callers
   * (the list-GET callback) iterate and filter the result outside the lock, concurrently with
   * writers — returning the live {@code TreeMap} here let that iteration race concurrent
   * {@code put()}s and intermittently miss entries that were already 201-created (~1% of
   * filtered list GETs under parallel load). The shallow copy is the fix; the {@code JsonNode}
   * values are shared but the write paths replace them, never mutate them in place.
   */
  public synchronized SortedMap<Id, JsonNode> getAll(String domain) {
    LOG.info(
        "Getting cache entries for domain = \"{}\". Existing domain list: {}",
        domain,
        dataCache.keySet());

    if (dataCache.get(domain) == null) {
      LOG.info(NO_CACHE_ENTRY_FOUND_FOR_DOMAIN, domain);
      return Collections.emptySortedMap();
    }
    return new TreeMap<>(dataCache.get(domain));
  }

  public synchronized void clear(RequestContext ctx) {
    dataCache.get(ctx.getDomain()).remove(ctx.getId());
    timeCache.get(ctx.getDomain()).remove(ctx.getId());
    LOG.info("Old cache entry for " + DOMAIN_WITH + " is removed", ctx.getDomain(), ctx.getId());
  }

  private synchronized void evictOldItems() {
    LOG.info(START_EVICTING_OLD_CACHE_ITEMS);
    long now = System.currentTimeMillis();
    IdempotencyCache idempotencyCache = IdempotencyCache.getInstance();
    for (String domain : new ArrayList<>(timeCache.keySet())) {
      List<Id> expiredKeys = new ArrayList<>();
      for (Map.Entry<Id, Long> entry : timeCache.get(domain).entrySet()) {
        if (now - entry.getValue() >= timeToLive) {
          expiredKeys.add(entry.getKey());
        }
      }
      for (Id key : expiredKeys) {
        timeCache.get(domain).remove(key);
        dataCache.get(domain).remove(key);
        idempotencyCache.evictForResource(domain, key);
        LOG.info("Old cache entry for " + DOMAIN_WITH + " is removed", domain, key);
      }
    }
    LOG.info("Evicting old cache items completed.");
  }

  private static Id allOf(Id key) {
    Id key2 = new Id();
    key2.setId(key.getId());
    String suffix = String.valueOf(Character.MAX_VALUE);
    key2.setVersion(key.getVersion() == null ? suffix : key.getVersion() + suffix);
    return key2;
  }
}
