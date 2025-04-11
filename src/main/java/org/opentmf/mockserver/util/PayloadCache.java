package org.opentmf.mockserver.util;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
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
  public static final String NO_CACHE_ENTRY_FOUND_FOR_DOMAIN = "No cache entry found for domain = '{}'";

  private final Map<String, TreeMap<String, JsonNode>> dataCache = new HashMap<>();
  private final Map<String, TreeMap<String, Long>> timeCache = new HashMap<>();
  private static final long PROD_TTL = 1000L * 60 * 60 * 2;

  private final long timeToLive;

  private PayloadCache(long timeToLive) {
    this.timeToLive = timeToLive;
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
      instance = new PayloadCache(PROD_TTL);
    }
    return instance;
  }

  // TimerTask for cache eviction
  private static class CacheEvictTimer extends TimerTask {
    @Override
    public void run() {
      getInstance().evictOldItems();
    }
  }

  /**
   * Stores a payload in the cache with the specified domain and key.
   *
   * @param domain The domain identifier for the cache entry.
   * @param key The key identifier for the cache entry.
   * @param value The payload to be cached.
   */
  public synchronized void put(String domain, String key, JsonNode value) {
    dataCache.putIfAbsent(domain, new TreeMap<>());
    timeCache.putIfAbsent(domain, new TreeMap<>());

    if (dataCache.get(domain).containsKey(key)) {
      throw new IllegalArgumentException();
    }

    touch(domain, key);
    dataCache.get(domain).put(key, value);
    LOG.debug("Cache entry for domain = '{}' and key = '{}' added", domain, key);
  }

  /**
   * Updates an existing cache entry with the specified domain and key.
   *
   * @param domain The domain identifier for the cache entry.
   * @param key The key identifier for the cache entry.
   * @param value The updated payload.
   */
  public synchronized void update(String domain, String key, JsonNode value) {
    if (!dataCache.get(domain).containsKey(key)) {
      throw new IllegalArgumentException();
    }

    touch(domain, key);
    dataCache.get(domain).put(key, value);
  }

  /**
   * Updates the last access time for the specified cache entry.
   *
   * @param domain The domain identifier for the cache entry.
   * @param key The key identifier for the cache entry.
   */
  public synchronized void touch(String domain, String key) {
    timeCache.get(domain).subMap(key, true, key + "z", true)
        .replaceAll((k, v) -> System.currentTimeMillis());
  }

  public synchronized JsonNode getLatestOf(String domain, String key) {
    if (dataCache.get(domain) == null) {
      LOG.info(NO_CACHE_ENTRY_FOUND_FOR_DOMAIN, domain);
      return null;
    }
    TreeMap<String, JsonNode> map = dataCache.get(domain);
    return map.subMap(key, true, key + "z", true).lastEntry().getValue();
  }

  public synchronized String getLatestVersion(String domain, String key) {
    if (dataCache.get(domain) == null) {
      LOG.info(NO_CACHE_ENTRY_FOUND_FOR_DOMAIN, domain);
      return null;
    }
    TreeMap<String, JsonNode> map = dataCache.get(domain);
    return map.subMap(key, true, key + "z", true)
        .lastEntry()
        .getValue()
        .get("version")
        .asText();
  }

  /**
   * Retrieves the payload associated with the specified domain and key.
   *
   * @param domain The domain identifier for the cache entry.
   * @param key The key identifier for the cache entry.
   * @return The payload associated with the specified domain and key, or null if not found.
   */
  public synchronized JsonNode get(String domain, String key) {
    LOG.info("Getting cache entry for domain = '{}' and key = '{}'", domain, key);

    if (dataCache.get(domain) == null) {
      LOG.info(NO_CACHE_ENTRY_FOUND_FOR_DOMAIN, domain);
      return null;
    }
    return dataCache.get(domain).get(key);
  }

  /**
   * Retrieves all cache entries associated with the specified domain.
   *
   * @param domain The domain identifier for the cache entries.
   * @return A map containing all cache entries for the specified domain.
   */
  public synchronized Map<String, JsonNode> get(String domain) {
    if (dataCache.get(domain) == null) {
      LOG.info(NO_CACHE_ENTRY_FOUND_FOR_DOMAIN, domain);
      return Collections.emptyMap();
    }
    return dataCache.get(domain);
  }

  /**
   * Checks if the cache is empty for the specified domain.
   *
   * @param domain The domain identifier to check.
   * @return true if the cache is empty for the specified domain, false otherwise.
   */
  public synchronized boolean isEmpty(String domain) {
    return dataCache.get(domain).isEmpty();
  }

  /**
   * Clears a specific cache entry identified by domain and key.
   *
   * @param domain The domain identifier for the cache entry.
   * @param key The key identifier for the cache entry.
   */
  public synchronized void clear(String domain, String key) {
    dataCache.get(domain).remove(key);
    timeCache.get(domain).remove(key);
    LOG.info("Old cache entry for domain = '{}' and key = '{}' is removed", domain, key);
  }

  /**
   * Clears all cache entries associated with the specified domain.
   *
   * @param domain The domain identifier for which cache entries will be cleared.
   */
  public synchronized void clear(String domain) {
    dataCache.remove(domain);
    timeCache.remove(domain);
    LOG.info("All cache entries for domain = '{}' are removed", domain);
  }

  // Evicts old cache entries based on time-to-live (TTL)
  private void evictOldItems() {
    LOG.info("Start evicting old cache items.");
    timeCache
        .keySet()
        .forEach(
            domain -> {
              for (Iterator<String> iterator = timeCache.get(domain).keySet().iterator();
                  iterator.hasNext(); ) {
                String key = iterator.next();
                long t = timeCache.get(domain).get(key);
                if (System.currentTimeMillis() - t >= timeToLive) {
                  iterator.remove();
                  clear(domain, key);
                }
              }
            });
    LOG.info("Evicting old cache items completed.");
  }
}
