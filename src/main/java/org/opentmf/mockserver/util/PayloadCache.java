package org.opentmf.mockserver.util;

import static org.opentmf.mockserver.model.TmfConstants.VERSION;
import static org.opentmf.mockserver.util.Constants.CACHE_DURATION_MILLIS;
import static org.opentmf.mockserver.util.Constants.TWO_HOURS;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import org.opentmf.mockserver.model.Id;
import org.opentmf.mockserver.model.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for caching payloads with time-based eviction. This class provides methods to put,
 * update, get, and clear cache entries. Cache entries are stored as maps with domain and key
 * identifiers. Cache eviction is performed based on a specified time-to-live (TTL) for entries.
 *
 * @author Gokhan Demir
 */
public class PayloadCache {

  private static final Logger LOG = LoggerFactory.getLogger(PayloadCache.class);
  private static final String NO_CACHE_ENTRY_FOUND_FOR_DOMAIN = "No cache entry found for domain = \"{}\"";
  private static final String START_EVICTING_OLD_CACHE_ITEMS = "Start evicting old cache items.";
  private static final String DOMAIN_WITH = "domain = \"{}\" with [{}]";

  private final Map<String, TreeMap<Id, JsonNode>> dataCache = new LinkedHashMap<>();
  private final Map<String, TreeMap<Id, Long>> timeCache = new HashMap<>();

  private final long timeToLive;

  private PayloadCache(long timeToLive) {
    this.timeToLive = timeToLive;
    LOG.info("Cache initialized to expire in {}", DurationUtil.formatDuration(timeToLive));
    // Schedule a timer task for cache eviction
    new Timer().scheduleAtFixedRate(new CacheEvictTimer(), 0L, timeToLive);
  }

  // Singleton instance of PayloadCache
  private static PayloadCache instance = null;

  /**
   * Returns the singleton instance of PayloadCache with default TTL.
   *
   * @return The singleton instance of PayloadCache.
   */
  public static PayloadCache getInstance() {
    if (instance == null) {
      String cacheDurationMillis = System.getenv(CACHE_DURATION_MILLIS);
      long milliseconds = Long.parseLong(cacheDurationMillis == null ? TWO_HOURS : cacheDurationMillis);
      instance = new PayloadCache(milliseconds);
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
      throw new IllegalArgumentException("Key: [" + ctx.getId() + "] already exists in cache for domain ");
    }

    touch(ctx);
    dataCache.get(ctx.getDomain()).put(ctx.getId(), value);
    LOG.info("Cache entry for " + DOMAIN_WITH + " added", ctx.getDomain(), ctx.getId());
  }

  public synchronized void update(RequestContext ctx, JsonNode value) {
    if (!dataCache.get(ctx.getDomain()).containsKey(ctx.getId())) {
      throw new IllegalArgumentException();
    }
    touch(ctx);
    dataCache.get(ctx.getDomain()).put(ctx.getId(), value);
  }

  // Update the last access time of cached data in the cache
  public synchronized void touch(RequestContext ctx) {
    Id key = new Id();
    key.setId(ctx.getId().getId());
    key.setVersion("");
    timeCache.get(ctx.getDomain()).subMap(key, true, allOf(key), true)
        .replaceAll((k, v) -> System.currentTimeMillis());
  }

  public synchronized JsonNode getLatestOf(RequestContext ctx) {
    if (dataCache.get(ctx.getDomain()) == null) {
      LOG.info(NO_CACHE_ENTRY_FOUND_FOR_DOMAIN, ctx.getDomain());
      return null;
    }
    TreeMap<Id, JsonNode> map = dataCache.get(ctx.getDomain());
    return map.subMap(ctx.getId(), true, allOf(ctx.getId()), true).lastEntry().getValue();
  }

  public synchronized String getLatestVersion(String domain, Id key) {
    if (dataCache.get(domain) == null) {
      LOG.info(NO_CACHE_ENTRY_FOUND_FOR_DOMAIN, domain);
      return null;
    }
    TreeMap<Id, JsonNode> map = dataCache.get(domain);
    return map.subMap(key, true, allOf(key), true)
        .lastEntry()
        .getValue()
        .get(VERSION)
        .asText();
  }

  public synchronized JsonNode get(RequestContext ctx) {
    LOG.info("Getting cache entry for " + DOMAIN_WITH, ctx.getDomain(), ctx.getId());

    if (dataCache.get(ctx.getDomain()) == null) {
      LOG.info(NO_CACHE_ENTRY_FOUND_FOR_DOMAIN, ctx.getDomain());
      return null;
    }
    return dataCache.get(ctx.getDomain()).get(ctx.getId());
  }

  public synchronized SortedMap<Id, JsonNode> getAll(String domain) {
    LOG.info("Getting cache entries for domain = \"{}\". Existing domain list: {}", domain, dataCache.keySet());

    if (dataCache.get(domain) == null) {
      LOG.info(NO_CACHE_ENTRY_FOUND_FOR_DOMAIN, domain);
      return Collections.emptySortedMap();
    }
    return dataCache.get(domain);
  }

  /**
   * Clears a specific cache entry identified by domain and key.
   *
   * @param domain The domain identifier for the cache entry.
   * @param key The key identifier for the cache entry.
   */
  private synchronized void clear(String domain, Id key) {
    dataCache.get(domain).remove(key);
    timeCache.get(domain).remove(key);
    LOG.info("Old cache entry for " + DOMAIN_WITH + " is removed", domain, key);
  }

  public synchronized void clear(RequestContext ctx) {
    dataCache.get(ctx.getDomain()).remove(ctx.getId());
    timeCache.get(ctx.getDomain()).remove(ctx.getId());
    LOG.info("Old cache entry for " + DOMAIN_WITH + " is removed", ctx.getDomain(),
        ctx.getId());
  }

  // Evicts old cache entries based on time-to-live (TTL)
  private void evictOldItems() {
    LOG.info(START_EVICTING_OLD_CACHE_ITEMS);
    timeCache
        .keySet()
        .forEach(
            domain -> {
              for (Iterator<Id> iterator = timeCache.get(domain).keySet().iterator();
                  iterator.hasNext(); ) {
                Id key = iterator.next();
                long t = timeCache.get(domain).get(key);
                if (System.currentTimeMillis() - t >= timeToLive) {
                  iterator.remove();
                  clear(domain, key);
                }
              }
            });
    LOG.info("Evicting old cache items completed.");
  }

  private static Id allOf(Id key) {
    Id key2 = new Id();
    key2.setId(key.getId());
    key2.setVersion(key.getVersion() == null ? "z" : key.getVersion() + "z");
    return key2;
  }
}
