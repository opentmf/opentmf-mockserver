package org.opentmf.mockserver.util;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.NottableString;
import org.mockserver.model.Parameter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.MissingNode;
import tools.jackson.databind.node.NullNode;

public final class CacheQuery {
  private static final ObjectMapper MAPPER = JsonMapper.builder().build();

  /** One field==value condition. Path may be dot notation, [index], or a JSON Pointer if it starts with '/'. */
  public static final class Criterion {
    private final String path;
    private final Object expected;

    public Criterion(String path, Object expected) {
      this.path = path;
      this.expected = expected;
    }
    public String path() { return path; }
    public Object expected() { return expected; }
    JsonNode expectedNode() { return toJsonNode(expected); }
  }

  /** Core API: filter by a list of criteria. Keys may repeat; repeated keys are OR'ed, different keys are AND'ed. */
  public static <K> Map<K, JsonNode> filter(SortedMap<K, JsonNode> cache, List<Criterion> criteria) {
    if (criteria == null || criteria.isEmpty()) {
      return new LinkedHashMap<>(cache);
    }

    // Group by path → list of expected values (OR within group)
    Map<String, List<JsonNode>> grouped = new LinkedHashMap<>();
    for (Criterion c : criteria) {
      grouped.computeIfAbsent(c.path(), k -> new ArrayList<>()).add(c.expectedNode());
    }

    return cache.entrySet().stream()
        .filter(e -> matchesGrouped(e.getValue(), grouped))
        .collect(Collectors.toMap(
            Map.Entry::getKey,
            Map.Entry::getValue,
            (a, b) -> a,
            LinkedHashMap::new
        ));
  }

  private static final Set<String> IGNORED_KEYS = Set.of("limit", "offset", "sort", "fields", "filter");

  public static <K> Map<K, JsonNode> filter(SortedMap<K, JsonNode> cache, HttpRequest httpRequest) {
    List<Criterion> list = new ArrayList<>();

    for (Parameter p : httpRequest.getQueryStringParameterList()) {
      String path = p.getName().getValue();
      if (IGNORED_KEYS.contains(path)) {
        continue;
      }
      for (NottableString v : p.getValues()) {
        list.add(new Criterion(path, URLDecoder.decode(v.getValue(), StandardCharsets.UTF_8)));
      }
    }
    return filter(cache, list);
  }

  /* ---------- Internals ---------- */

  /** AND across groups (paths), OR within each group's expected values. */
  private static boolean matchesGrouped(JsonNode doc, Map<String, List<JsonNode>> grouped) {
    for (Map.Entry<String, List<JsonNode>> entry : grouped.entrySet()) {
      String path = entry.getKey();
      List<JsonNode> expectedList = entry.getValue();

      JsonNode actual = readPath(doc, path);

      if (actual.isMissingNode()) {
        // Missing only matches if any expected is null
        if (!containsNull(expectedList)) return false;
        continue; // this path satisfied (null-or-missing)
      }

      boolean matchedAny = false;
      for (JsonNode expected : expectedList) {
        if (expected.isNull()) {
          if (actual.isNull()) { matchedAny = true; break; }
          // if actual present & non-null, null doesn't match; keep checking others
        } else if (jsonEquals(actual, expected)) {
          matchedAny = true; break;
        }
      }
      if (!matchedAny) return false; // AND fails
    }
    return true;
  }

  private static boolean containsNull(List<JsonNode> list) {
    for (JsonNode n : list) if (n.isNull()) return true;
    return false;
  }

  /** Read a value by dot/bracket path or JSON Pointer. */
  private static JsonNode readPath(JsonNode root, String path) {
    if (path.startsWith("/")) { // JSON Pointer
      return root.at(path);
    }
    JsonNode cur = root;
    String[] tokens = path.split("\\.");
    for (String token : tokens) {
      if (token.isEmpty()) return MissingNode.getInstance();

      int idxStart = token.indexOf('[');
      if (idxStart >= 0) {
        String field = token.substring(0, idxStart);
        cur = cur.path(field);
        while (idxStart >= 0) {
          int idxEnd = token.indexOf(']', idxStart);
          if (idxEnd < 0) return MissingNode.getInstance();
          String idxStr = token.substring(idxStart + 1, idxEnd);
          int idx;
          try {
            idx = Integer.parseInt(idxStr);
          } catch (NumberFormatException ex) {
            return MissingNode.getInstance();
          }
          if (!cur.isArray() || idx < 0 || idx >= cur.size()) return MissingNode.getInstance();
          cur = cur.get(idx);
          idxStart = token.indexOf('[', idxEnd + 1);
        }
      } else {
        cur = cur.path(token);
      }
      if (cur.isMissingNode()) return cur;
    }
    return cur;
  }

  /** Equality that is friendly to numbers (1 == 1.0) and otherwise uses JsonNode deep equality. */
  private static boolean jsonEquals(JsonNode a, JsonNode b) {
    if (a.getNodeType() != b.getNodeType()) {
      // Coerce textual numbers ↔ numeric nodes when possible
      if (a.isNumber() && b.isTextual()) {
        try { return new BigDecimal(b.asText()).compareTo(a.decimalValue()) == 0; }
        catch (NumberFormatException ignore) {}
      }
      if (b.isNumber() && a.isTextual()) {
        try { return new BigDecimal(a.asText()).compareTo(b.decimalValue()) == 0; }
        catch (NumberFormatException ignore) {}
      }
      return false;
    }
    if (a.isNumber()) {
      return a.decimalValue().compareTo(b.decimalValue()) == 0;
    }
    return a.equals(b);
  }

  private static JsonNode toJsonNode(Object value) {
    if (value == null) return NullNode.getInstance();
    if (value instanceof JsonNode) return (JsonNode) value;
    return MAPPER.valueToTree(value);
  }
}
