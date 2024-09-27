package com.pia.mockserver.util;

import static com.pia.mockserver.model.TmfConstants.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Utility class for setting audit fields in JSON objects. This class provides methods to set
 * audit-related fields such as created date, updated date, created by, updated by, and revision.
 *
 * @author Yusuf BOZKURT
 */
public class AuditFieldUtil {
  private AuditFieldUtil() {}

  /**
   * Sets update-related audit fields in the provided JSON object.
   *
   * @param objectNode The JSON object to which update-related audit fields will be added.
   */
  public static void setUpdateFields(ObjectNode objectNode) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    String updatedUser = RandomStringUtils.randomString(10);
    objectNode.put(UPDATED_DATE, now.toString());
    objectNode.put(UPDATED_BY, updatedUser);

    long revision = -1L;
    if (objectNode.get(REVISION) != null) {
      revision = objectNode.get(REVISION).asLong();
    }
    objectNode.put(REVISION, revision + 1);
  }

  /**
   * Sets create-related audit fields in the provided JSON object.
   *
   * @param objectNode The JSON object to which create-related audit fields will be added.
   */
  public static void setCreateFields(ObjectNode objectNode) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    String updatedUser = RandomStringUtils.randomString(10);
    objectNode.put(CREATED_DATE, now.toString());
    objectNode.put(CREATED_BY, updatedUser);
    objectNode.put(REVISION, 0L);
  }
}
