package org.opentmf.mockserver.util;

import static org.opentmf.mockserver.model.TmfConstants.ID;
import static org.opentmf.mockserver.model.TmfConstants.VERSION;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.opentmf.mockserver.model.Id;
import java.util.Locale;
import org.apache.commons.lang3.RandomStringUtils;

public final class IdExtractor {

  private IdExtractor() {}

  public static Id extractId(boolean versioned, ObjectNode parsedBody) {
    Id id = new Id();
    if (versioned) {
      if (!parsedBody.has(VERSION)) {
        parsedBody.put(VERSION, "0");
      }
      id.setVersion(parsedBody.get(VERSION).asText());
    }
    id.setProvided(parsedBody.has(ID));
    id.setId(parsedBody.has(ID) ? parsedBody.get(ID).asText() : produceNewId());
    parsedBody.put(ID, id.getId());
    return id;
  }

  /** 2:(version=0) */
  public static Id parseId(String pureId) {
    Id id = new Id();
    if (pureId.toLowerCase(Locale.UK).contains(":(version=")) {
      id.setProvided(true);
      id.setVersion(pureId.substring(pureId.indexOf("version=") + 8, pureId.length() - 1));
      id.setId(pureId.substring(0, pureId.indexOf(":(version=")));
    } else if (pureId.contains("version=")) {
      id.setProvided(true);
      String s = pureId.substring(pureId.indexOf("version=") + 8);
      id.setVersion(s.contains("&") ? s.substring(0, s.indexOf("&")) : s);
      id.setId(pureId.contains("?") ? pureId.substring(0, pureId.indexOf("?")) : pureId);
    } else {
      id.setProvided(false);
      id.setId(pureId);
    }
    return id;
  }

  private static String produceNewId() {
    return RandomStringUtils.random(10, "0123467789ABC-DEF");
  }
}
