package com.pia.mockserver.util;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class JacksonUtilTests {
  @Test
  void testReadAsTree() {
    String json = "{\"key\":\"value\"}";
    JsonNode node = JacksonUtil.readAsTree(json);
    assertEquals("value", node.get("key").asText());
  }

  @Test
  void testConvertValue() {
    ObjectNode node = JacksonUtil.createObjectNode();
    node.put("key", "value");
    TestObject testObject = JacksonUtil.convertValue(node, TestObject.class);
    assertEquals("value", testObject.getKey());
  }

  @Test
  void testWriteAsString() {
    TestObject testObject = new TestObject();
    testObject.setKey("value");
    String json = JacksonUtil.writeAsString(testObject);
    assertEquals("{\"key\":\"value\"}", json);
  }

  @Test
  void testConvertToJsonNodeList() {
    ObjectNode node1 = JacksonUtil.createObjectNode();
    node1.put("key", "value1");
    ObjectNode node2 = JacksonUtil.createObjectNode();
    node2.put("key", "value2");
    List<Object> list = Arrays.asList(node1, node2);
    List<JsonNode> nodeList = JacksonUtil.convertToJsonNodeList(list);
    assertEquals(2, nodeList.size());
    assertEquals("value1", nodeList.get(0).get("key").asText());
    assertEquals("value2", nodeList.get(1).get("key").asText());
  }

  @Test
  void testCreateArrayNode() {
    ArrayNode arrayNode = JacksonUtil.createArrayNode();
    assertNotNull(arrayNode);
  }

  @Test
  void testCreateObjectNode() {
    ObjectNode objectNode = JacksonUtil.createObjectNode();
    assertNotNull(objectNode);
  }

  @Test
  void testReadAsTreeWithInvalidJson() {
    String json = "invalid json";
    assertThrows(IllegalArgumentException.class, () -> JacksonUtil.readAsTree(json));
  }

  @Test
  void testConvertValueWithInvalidType() {
    ObjectNode node = JacksonUtil.createObjectNode();
    node.put("key", "value");
    assertThrows(IllegalArgumentException.class, () -> JacksonUtil.convertValue(node, List.class));
  }

  @Test
  void testWriteAsStringWithInvalidObject() {
    Object invalidObject = new Object();
    assertThrows(IllegalArgumentException.class, () -> JacksonUtil.writeAsString(invalidObject));
  }

  @Test
  void testReadAsJsonMergerWithInvalidJson() {
    String json = "invalid json";
    assertThrows(IllegalArgumentException.class, () -> JacksonUtil.readAsJsonMerger(json));
  }

  @Test
  void testConvertToJsonNodeListWithInvalidList() {
    List<Object> list = Arrays.asList(new Object(), new Object());
    assertThrows(IllegalArgumentException.class, () -> JacksonUtil.convertToJsonNodeList(list));
  }

  private static class TestObject {
    private String key;

    public String getKey() {
      return key;
    }

    public void setKey(String key) {
      this.key = key;
    }
  }
}
