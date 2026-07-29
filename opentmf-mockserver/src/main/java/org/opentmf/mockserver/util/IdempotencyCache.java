package org.opentmf.mockserver.util;

import static org.opentmf.mockserver.util.Constants.CACHE_DURATION_MILLIS;
import static org.opentmf.mockserver.util.Constants.TWO_HOURS;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import org.opentmf.mockserver.model.Id;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stores responses produced by previous successful, idempotency-keyed mutations so that retries
 * carrying the same {@code Idempotency-Key} header can be replayed verbatim. Records expire on the
 * same TTL as {@link PayloadCache} (overridable via {@code CACHE_DURATION_MILLIS}) and are also
 * evicted eagerly by {@link PayloadCache} when their underlying resource is removed.
 *
 * <p>Lookup key is the triple (HTTP method, request path, idempotency-key value). The body is not
 * fingerprinted; a same-key retry on a different path is treated as a conflict by {@link
 * IdempotencyGuard} and returned to the client as 422.
 *
 * @author Gokhan Demir
 */
public class IdempotencyCache {

  private static final Logger LOG = LoggerFactory.getLogger(IdempotencyCache.class);

  private final Map<String, Record> records = new HashMap<>();
  private final long timeToLive;

  private IdempotencyCache(long timeToLive) {
    this.timeToLive = timeToLive;
    LOG.info(
        "Idempotency cache initialized to expire in {}",
        DurationUtil.formatDuration(timeToLive));
    new Timer(true).scheduleAtFixedRate(new EvictTimer(), timeToLive, timeToLive);
  }

  private static volatile IdempotencyCache instance = null;

  public static IdempotencyCache getInstance() {
    if (instance == null) {
      synchronized (IdempotencyCache.class) {
        if (instance == null) {
          String cacheDurationMillis = System.getenv(CACHE_DURATION_MILLIS);
          long milliseconds =
              Long.parseLong(cacheDurationMillis == null ? TWO_HOURS : cacheDurationMillis);
          instance = new IdempotencyCache(milliseconds);
        }
      }
    }
    return instance;
  }

  /**
   * Record of a previously completed mutation, plus the (domain, id) of the resource it created or
   * touched so that {@link PayloadCache} can eagerly evict matching idempotency records when the
   * underlying payload is removed.
   */
  public static final class Record {
    private final String method;
    private final String path;
    private final int statusCode;
    private final String contentType;
    private final String body;
    private final String domain;
    private final Id resourceId;
    private final long completedAt;

    public Record(
        String method,
        String path,
        int statusCode,
        String contentType,
        String body,
        String domain,
        Id resourceId,
        long completedAt) {
      this.method = method;
      this.path = path;
      this.statusCode = statusCode;
      this.contentType = contentType;
      this.body = body;
      this.domain = domain;
      this.resourceId = resourceId;
      this.completedAt = completedAt;
    }

    public String getMethod() {
      return method;
    }

    public String getPath() {
      return path;
    }

    public int getStatusCode() {
      return statusCode;
    }

    public String getContentType() {
      return contentType;
    }

    public String getBody() {
      return body;
    }

    public String getDomain() {
      return domain;
    }

    public Id getResourceId() {
      return resourceId;
    }

    public long getCompletedAt() {
      return completedAt;
    }
  }

  public synchronized Record get(String idempotencyKey) {
    return records.get(idempotencyKey);
  }

  public synchronized void put(String idempotencyKey, Record record) {
    records.put(idempotencyKey, record);
    LOG.info(
        "Idempotency record stored for key=\"{}\" {} {}",
        idempotencyKey,
        record.getMethod(),
        record.getPath());
  }

  /**
   * Drops every record that references {@code (domain, resourceId)}. Called by {@link PayloadCache}
   * when the underlying resource is removed, so a same-key retry never replays a 2xx for a resource
   * that no longer exists.
   */
  public synchronized void evictForResource(String domain, Id resourceId) {
    List<String> toRemove = new ArrayList<>();
    for (Map.Entry<String, Record> e : records.entrySet()) {
      Record rec = e.getValue();
      if (Objects.equals(rec.getDomain(), domain)
          && Objects.equals(rec.getResourceId(), resourceId)) {
        toRemove.add(e.getKey());
      }
    }
    for (String key : toRemove) {
      records.remove(key);
      LOG.info("Idempotency record dropped for key=\"{}\" (resource evicted)", key);
    }
  }

  public synchronized void clear() {
    records.clear();
  }

  private class EvictTimer extends TimerTask {
    @Override
    public void run() {
      synchronized (IdempotencyCache.this) {
        long now = System.currentTimeMillis();
        List<String> expired = new ArrayList<>();
        for (Map.Entry<String, Record> e : records.entrySet()) {
          if (now - e.getValue().getCompletedAt() >= timeToLive) {
            expired.add(e.getKey());
          }
        }
        for (String k : expired) {
          records.remove(k);
          LOG.info("Idempotency record expired for key=\"{}\"", k);
        }
      }
    }
  }
}
