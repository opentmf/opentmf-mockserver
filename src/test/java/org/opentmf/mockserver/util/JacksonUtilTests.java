package org.opentmf.mockserver.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

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
    assertThrows(Exception.class, () -> JacksonUtil.convertValue(node, List.class));
  }

  @Test
  void testWriteAsStringWithPlainObject() {
    Object obj = new Object();
    String json = JacksonUtil.writeAsString(obj);
    assertNotNull(json);
  }

  @Test
  void testConvertToJsonNodeListWithPlainObjects() {
    List<Object> list = Arrays.asList(new Object(), new Object());
    List<JsonNode> result = JacksonUtil.convertToJsonNodeList(list);
    assertNotNull(result);
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
