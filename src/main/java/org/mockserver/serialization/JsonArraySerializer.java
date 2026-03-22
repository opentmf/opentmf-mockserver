package org.mockserver.serialization;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

/**
 * @author jamesdbloom
 */
public class JsonArraySerializer {
  private static final ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();

  public List<String> splitJSONArray(String jsonArray) {
    return splitJSONArrayToJSONNodes(jsonArray).stream()
        .map(
            node -> {
              try {
                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
              } catch (Exception e) {
                return node.toString();
              }
            })
        .collect(Collectors.toList());
  }

  public List<JsonNode> splitJSONArrayToJSONNodes(String jsonArray) {
    List<JsonNode> arrayItems = new ArrayList<>();
    try {
      JsonNode jsonNode = objectMapper.readTree(jsonArray);
      if (jsonNode instanceof ArrayNode) {
        for (JsonNode arrayElement : jsonNode) {
          arrayItems.add(arrayElement);
        }
      } else {
        arrayItems.add(jsonNode);
      }
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
    return arrayItems;
  }
}
