package org.opentmf.mockserver.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.mockserver.model.HttpRequest;
import org.opentmf.mockserver.model.Id;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.MissingNode;
import tools.jackson.databind.node.NullNode;

class CacheQueryTests {

  private static Id id(String s) {
    Id key = new Id();
    key.setId(s);
    return key;
  }

  private static SortedMap<Id, JsonNode> cacheOf(Map<String, String> entries) {
    SortedMap<Id, JsonNode> cache = new TreeMap<>();
    entries.forEach((k, v) -> cache.put(id(k), JacksonUtil.readAsTree(v)));
    return cache;
  }

  // ---- Criterion (zero-coverage record) ----

  @Test
  void criterion_expectedNode_wrapsNull_asNullNode() {
    CacheQuery.Criterion c = new CacheQuery.Criterion("x", null);
    assertEquals(NullNode.getInstance(), c.expectedNode());
  }

  @Test
  void criterion_expectedNode_returnsJsonNodeUnwrapped() {
    JsonNode node = JacksonUtil.readAsTree("{\"a\":1}");
    CacheQuery.Criterion c = new CacheQuery.Criterion("x", node);
    assertNotNull(c.expectedNode());
    assertTrue(c.expectedNode().isObject());
    assertEquals(1, c.expectedNode().get("a").asInt());
  }

  @Test
  void criterion_expectedNode_convertsPlainStringViaMapper() {
    CacheQuery.Criterion c = new CacheQuery.Criterion("x", "hello");
    assertEquals("hello", c.expectedNode().asString());
  }

  @Test
  void criterion_expectedNode_convertsNumberViaMapper() {
    CacheQuery.Criterion c = new CacheQuery.Criterion("x", 42);
    assertTrue(c.expectedNode().isNumber());
    assertEquals(42, c.expectedNode().asInt());
  }

  @Test
  void criterion_recordAccessors() {
    CacheQuery.Criterion c = new CacheQuery.Criterion("path.to.field", "value");
    assertEquals("path.to.field", c.path());
    assertEquals("value", c.expected());
  }

  // ---- filter(cache, List<Criterion>) — null / empty criteria ----

  @Test
  void filter_nullCriteria_returnsCopyOfCache() {
    SortedMap<Id, JsonNode> cache = cacheOf(Map.of("a", "{\"x\":1}"));
    Map<Id, JsonNode> result = CacheQuery.filter(cache, (List<CacheQuery.Criterion>) null);
    assertEquals(1, result.size());
    assertNotSame(cache, result);
  }

  @Test
  void filter_emptyCriteria_returnsCopyOfCache() {
    SortedMap<Id, JsonNode> cache = cacheOf(Map.of("a", "{\"x\":1}"));
    Map<Id, JsonNode> result = CacheQuery.filter(cache, List.of());
    assertEquals(1, result.size());
  }

  // ---- basic matching ----

  @Test
  void filter_simpleFieldEquality_returnsMatches() {
    SortedMap<Id, JsonNode> cache =
        cacheOf(Map.of("a", "{\"status\":\"active\"}", "b", "{\"status\":\"inactive\"}"));
    Map<Id, JsonNode> result =
        CacheQuery.filter(cache, List.of(new CacheQuery.Criterion("status", "active")));
    assertEquals(1, result.size());
    assertTrue(result.containsKey(id("a")));
  }

  @Test
  void filter_noMatches_returnsEmpty() {
    SortedMap<Id, JsonNode> cache = cacheOf(Map.of("a", "{\"status\":\"active\"}"));
    Map<Id, JsonNode> result =
        CacheQuery.filter(cache, List.of(new CacheQuery.Criterion("status", "gone")));
    assertTrue(result.isEmpty());
  }

  // ---- AND across distinct paths, OR within same path ----

  @Test
  void filter_distinctPaths_areAnded() {
    SortedMap<Id, JsonNode> cache =
        cacheOf(
            Map.of(
                "a", "{\"status\":\"active\",\"tier\":\"gold\"}",
                "b", "{\"status\":\"active\",\"tier\":\"silver\"}"));
    Map<Id, JsonNode> result =
        CacheQuery.filter(
            cache,
            List.of(
                new CacheQuery.Criterion("status", "active"),
                new CacheQuery.Criterion("tier", "gold")));
    assertEquals(1, result.size());
    assertTrue(result.containsKey(id("a")));
  }

  @Test
  void filter_repeatedPath_isOred() {
    SortedMap<Id, JsonNode> cache =
        cacheOf(
            Map.of(
                "a", "{\"status\":\"active\"}",
                "b", "{\"status\":\"pending\"}",
                "c", "{\"status\":\"gone\"}"));
    Map<Id, JsonNode> result =
        CacheQuery.filter(
            cache,
            List.of(
                new CacheQuery.Criterion("status", "active"),
                new CacheQuery.Criterion("status", "pending")));
    assertEquals(2, result.size());
  }

  // ---- path styles: dot, JSON pointer, brackets ----

  @Test
  void filter_dotPath_navigatesNestedObject() {
    SortedMap<Id, JsonNode> cache =
        cacheOf(Map.of("a", "{\"nested\":{\"leaf\":\"x\"}}", "b", "{\"nested\":{\"leaf\":\"y\"}}"));
    Map<Id, JsonNode> result =
        CacheQuery.filter(cache, List.of(new CacheQuery.Criterion("nested.leaf", "x")));
    assertEquals(1, result.size());
  }

  @Test
  void filter_jsonPointerPath_startingWithSlash_worksAsPointer() {
    SortedMap<Id, JsonNode> cache =
        cacheOf(Map.of("a", "{\"nested\":{\"leaf\":\"x\"}}", "b", "{\"nested\":{\"leaf\":\"y\"}}"));
    Map<Id, JsonNode> result =
        CacheQuery.filter(cache, List.of(new CacheQuery.Criterion("/nested/leaf", "x")));
    assertEquals(1, result.size());
  }

  @Test
  void filter_dotPath_emptyToken_returnsMissing_noMatch() {
    SortedMap<Id, JsonNode> cache = cacheOf(Map.of("a", "{\"x\":1}"));
    Map<Id, JsonNode> result =
        CacheQuery.filter(cache, List.of(new CacheQuery.Criterion("x..y", "1")));
    assertTrue(result.isEmpty());
  }

  @Test
  void filter_bracketIndex_findsArrayElement() {
    SortedMap<Id, JsonNode> cache =
        cacheOf(
            Map.of(
                "a", "{\"tags\":[\"x\",\"y\"]}",
                "b", "{\"tags\":[\"z\"]}"));
    Map<Id, JsonNode> result =
        CacheQuery.filter(cache, List.of(new CacheQuery.Criterion("tags[0]", "x")));
    assertEquals(1, result.size());
    assertTrue(result.containsKey(id("a")));
  }

  @Test
  void filter_bracketIndex_chained() {
    SortedMap<Id, JsonNode> cache =
        cacheOf(Map.of("a", "{\"grid\":[[1,2],[3,4]]}"));
    Map<Id, JsonNode> result =
        CacheQuery.filter(cache, List.of(new CacheQuery.Criterion("grid[1][0]", 3)));
    assertEquals(1, result.size());
  }

  @Test
  void filter_bracketIndex_missingCloseBracket_returnsMissing() {
    SortedMap<Id, JsonNode> cache = cacheOf(Map.of("a", "{\"tags\":[\"x\"]}"));
    Map<Id, JsonNode> result =
        CacheQuery.filter(cache, List.of(new CacheQuery.Criterion("tags[0", "x")));
    assertTrue(result.isEmpty());
  }

  @Test
  void filter_bracketIndex_nonNumericIndex_returnsMissing() {
    SortedMap<Id, JsonNode> cache = cacheOf(Map.of("a", "{\"tags\":[\"x\"]}"));
    Map<Id, JsonNode> result =
        CacheQuery.filter(cache, List.of(new CacheQuery.Criterion("tags[abc]", "x")));
    assertTrue(result.isEmpty());
  }

  @Test
  void filter_bracketIndex_outOfBounds_returnsMissing() {
    SortedMap<Id, JsonNode> cache = cacheOf(Map.of("a", "{\"tags\":[\"x\"]}"));
    Map<Id, JsonNode> result =
        CacheQuery.filter(cache, List.of(new CacheQuery.Criterion("tags[5]", "x")));
    assertTrue(result.isEmpty());
  }

  @Test
  void filter_bracketIndex_negativeIndex_returnsMissing() {
    SortedMap<Id, JsonNode> cache = cacheOf(Map.of("a", "{\"tags\":[\"x\"]}"));
    Map<Id, JsonNode> result =
        CacheQuery.filter(cache, List.of(new CacheQuery.Criterion("tags[-1]", "x")));
    assertTrue(result.isEmpty());
  }

  @Test
  void filter_bracketIndex_onNonArray_returnsMissing() {
    SortedMap<Id, JsonNode> cache = cacheOf(Map.of("a", "{\"tags\":\"scalar\"}"));
    Map<Id, JsonNode> result =
        CacheQuery.filter(cache, List.of(new CacheQuery.Criterion("tags[0]", "s")));
    assertTrue(result.isEmpty());
  }

  // ---- missing path + null matching ----

  @Test
  void filter_missingPath_withoutNullExpectation_noMatch() {
    SortedMap<Id, JsonNode> cache = cacheOf(Map.of("a", "{\"x\":1}"));
    Map<Id, JsonNode> result =
        CacheQuery.filter(cache, List.of(new CacheQuery.Criterion("y", "1")));
    assertTrue(result.isEmpty());
  }

  @Test
  void filter_missingPath_withNullExpectation_matches() {
    SortedMap<Id, JsonNode> cache = cacheOf(Map.of("a", "{\"x\":1}"));
    Map<Id, JsonNode> result =
        CacheQuery.filter(cache, List.of(new CacheQuery.Criterion("y", null)));
    assertEquals(1, result.size());
  }

  @Test
  void filter_explicitNullFieldValue_matchesNullExpectation() {
    SortedMap<Id, JsonNode> cache = cacheOf(Map.of("a", "{\"x\":null}"));
    Map<Id, JsonNode> result =
        CacheQuery.filter(cache, List.of(new CacheQuery.Criterion("x", null)));
    assertEquals(1, result.size());
  }

  @Test
  void filter_nonNullFieldValue_doesNotMatchNullExpectation() {
    SortedMap<Id, JsonNode> cache = cacheOf(Map.of("a", "{\"x\":\"v\"}"));
    Map<Id, JsonNode> result =
        CacheQuery.filter(cache, List.of(new CacheQuery.Criterion("x", null)));
    assertTrue(result.isEmpty());
  }

  // ---- number/text coercion ----

  @Test
  void filter_numericFieldAgainstStringExpected_coerces() {
    SortedMap<Id, JsonNode> cache = cacheOf(Map.of("a", "{\"n\":10}"));
    Map<Id, JsonNode> result =
        CacheQuery.filter(cache, List.of(new CacheQuery.Criterion("n", "10")));
    assertEquals(1, result.size());
  }

  @Test
  void filter_stringFieldAgainstNumericExpected_coerces() {
    SortedMap<Id, JsonNode> cache = cacheOf(Map.of("a", "{\"n\":\"10.0\"}"));
    Map<Id, JsonNode> result =
        CacheQuery.filter(cache, List.of(new CacheQuery.Criterion("n", 10)));
    assertEquals(1, result.size());
  }

  @Test
  void filter_stringFieldNotParseableAsDecimal_noMatch() {
    SortedMap<Id, JsonNode> cache = cacheOf(Map.of("a", "{\"n\":\"not-a-number\"}"));
    Map<Id, JsonNode> result =
        CacheQuery.filter(cache, List.of(new CacheQuery.Criterion("n", 10)));
    assertTrue(result.isEmpty());
  }

  @Test
  void filter_typeMismatch_withoutNumericSide_noMatch() {
    SortedMap<Id, JsonNode> cache = cacheOf(Map.of("a", "{\"n\":true}"));
    Map<Id, JsonNode> result =
        CacheQuery.filter(cache, List.of(new CacheQuery.Criterion("n", "true")));
    assertTrue(result.isEmpty());
  }

  @Test
  void filter_numericEquality_ignoresIntVsDecimalRepresentation() {
    SortedMap<Id, JsonNode> cache = cacheOf(Map.of("a", "{\"n\":10}", "b", "{\"n\":10.0}"));
    Map<Id, JsonNode> result =
        CacheQuery.filter(cache, List.of(new CacheQuery.Criterion("n", 10)));
    assertEquals(2, result.size());
  }

  // ---- HttpRequest overload ----

  @Test
  void filter_httpRequest_readsQueryParams() {
    SortedMap<Id, JsonNode> cache =
        cacheOf(Map.of("a", "{\"status\":\"active\"}", "b", "{\"status\":\"gone\"}"));
    HttpRequest req = HttpRequest.request().withQueryStringParameter("status", "active");
    Map<Id, JsonNode> result = CacheQuery.filter(cache, req);
    assertEquals(1, result.size());
    assertTrue(result.containsKey(id("a")));
  }

  @Test
  void filter_httpRequest_ignoresPagingKeys() {
    SortedMap<Id, JsonNode> cache = cacheOf(Map.of("a", "{\"status\":\"active\"}"));
    HttpRequest req =
        HttpRequest.request()
            .withQueryStringParameter("limit", "10")
            .withQueryStringParameter("offset", "0")
            .withQueryStringParameter("sort", "created")
            .withQueryStringParameter("fields", "id,status")
            .withQueryStringParameter("filter", "status=active");
    Map<Id, JsonNode> result = CacheQuery.filter(cache, req);
    assertEquals(1, result.size());
  }

  @Test
  void filter_httpRequest_urlDecodesQueryValues() {
    SortedMap<Id, JsonNode> cache = cacheOf(Map.of("a", "{\"name\":\"John Doe\"}"));
    HttpRequest req = HttpRequest.request().withQueryStringParameter("name", "John%20Doe");
    Map<Id, JsonNode> result = CacheQuery.filter(cache, req);
    assertEquals(1, result.size());
  }

  @Test
  void filter_httpRequest_multipleValuesForSameKey_areOred() {
    SortedMap<Id, JsonNode> cache =
        cacheOf(Map.of("a", "{\"status\":\"active\"}", "b", "{\"status\":\"pending\"}"));
    HttpRequest req = HttpRequest.request().withQueryStringParameter("status", "active", "pending");
    Map<Id, JsonNode> result = CacheQuery.filter(cache, req);
    assertEquals(2, result.size());
  }

  @Test
  void filter_httpRequest_noQueryParams_returnsAll() {
    SortedMap<Id, JsonNode> cache =
        cacheOf(Map.of("a", "{\"status\":\"active\"}", "b", "{\"status\":\"gone\"}"));
    HttpRequest req = HttpRequest.request();
    Map<Id, JsonNode> result = CacheQuery.filter(cache, req);
    assertEquals(2, result.size());
  }

  // ---- MissingNode / NullNode sanity — implicitly exercised above but pinned here ----

  @Test
  void missingNode_getInstance_isSingleton() {
    assertNotNull(MissingNode.getInstance());
    assertTrue(MissingNode.getInstance().isMissingNode());
  }
}
