package org.opentmf.mockserver.util;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
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

  private static final Set<String> IGNORED_KEYS =
      Set.of("limit", "offset", "sort", "fields", "filter");

  private CacheQuery() {}

  /**
   * One field==value condition. Path may be dot notation, [index], or a JSON Pointer if it starts
   * with '/'.
   */
  public record Criterion(String path, Object expected) {
    JsonNode expectedNode() {
      if (expected == null) return NullNode.getInstance();
      if (expected instanceof JsonNode node) return node;
      return MAPPER.valueToTree(expected);
    }
  }

  /**
   * Core API: filter by a list of criteria. Keys may repeat; repeated keys are OR'ed, different
   * keys are AND'ed.
   */
  public static <K> Map<K, JsonNode> filter(
      SortedMap<K, JsonNode> cache, List<Criterion> criteria) {
    if (criteria == null || criteria.isEmpty()) {
      return new LinkedHashMap<>(cache);
    }

    Map<String, List<JsonNode>> grouped = new LinkedHashMap<>();
    for (Criterion c : criteria) {
      grouped.computeIfAbsent(c.path(), k -> new ArrayList<>()).add(c.expectedNode());
    }

    return cache.entrySet().stream()
        .filter(e -> matchesGrouped(e.getValue(), grouped))
        .collect(
            Collectors.toMap(
                Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
  }

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
      if (!matchesPath(doc, entry.getKey(), entry.getValue())) {
        return false;
      }
    }
    return true;
  }

  /** OR across the list of expected values at a single path. */
  private static boolean matchesPath(JsonNode doc, String path, List<JsonNode> expectedList) {
    JsonNode actual = readPath(doc, path);
    if (actual.isMissingNode()) {
      return containsNull(expectedList);
    }
    for (JsonNode expected : expectedList) {
      if (matchesValue(actual, expected)) {
        return true;
      }
    }
    return false;
  }

  private static boolean matchesValue(JsonNode actual, JsonNode expected) {
    if (expected.isNull()) {
      return actual.isNull();
    }
    return jsonEquals(actual, expected);
  }

  private static boolean containsNull(List<JsonNode> list) {
    for (JsonNode n : list) {
      if (n.isNull()) return true;
    }
    return false;
  }

  /** Read a value by dot/bracket path or JSON Pointer. */
  private static JsonNode readPath(JsonNode root, String path) {
    if (path.startsWith("/")) {
      return root.at(path);
    }
    JsonNode cur = root;
    for (String token : path.split("\\.")) {
      if (token.isEmpty()) {
        return MissingNode.getInstance();
      }
      cur = resolveToken(cur, token);
      if (cur.isMissingNode()) {
        return cur;
      }
    }
    return cur;
  }

  private static JsonNode resolveToken(JsonNode cur, String token) {
    int idxStart = token.indexOf('[');
    if (idxStart < 0) {
      return cur.path(token);
    }
    JsonNode node = cur.path(token.substring(0, idxStart));
    while (idxStart >= 0) {
      int idxEnd = token.indexOf(']', idxStart);
      if (idxEnd < 0) {
        return MissingNode.getInstance();
      }
      node = resolveIndex(node, token.substring(idxStart + 1, idxEnd));
      if (node.isMissingNode()) {
        return node;
      }
      idxStart = token.indexOf('[', idxEnd + 1);
    }
    return node;
  }

  private static JsonNode resolveIndex(JsonNode node, String idxStr) {
    int idx;
    try {
      idx = Integer.parseInt(idxStr);
    } catch (NumberFormatException ex) {
      return MissingNode.getInstance();
    }
    if (!node.isArray() || idx < 0 || idx >= node.size()) {
      return MissingNode.getInstance();
    }
    return node.get(idx);
  }

  /** Equality that is friendly to numbers (1 == 1.0) and otherwise uses JsonNode deep equality. */
  private static boolean jsonEquals(JsonNode a, JsonNode b) {
    if (a.getNodeType() != b.getNodeType()) {
      return coercedNumberEquals(a, b);
    }
    if (a.isNumber()) {
      return a.decimalValue().compareTo(b.decimalValue()) == 0;
    }
    return a.equals(b);
  }

  /** Coerce textual numbers ↔ numeric nodes when the two nodes differ in type. */
  private static boolean coercedNumberEquals(JsonNode a, JsonNode b) {
    if (a.isNumber() && b.isString()) {
      return parseDecimalMatches(b.asString(), a);
    }
    if (b.isNumber() && a.isString()) {
      return parseDecimalMatches(a.asString(), b);
    }
    return false;
  }

  private static boolean parseDecimalMatches(String text, JsonNode numeric) {
    try {
      return new BigDecimal(text).compareTo(numeric.decimalValue()) == 0;
    } catch (NumberFormatException ignored) {
      return false;
    }
  }
}
