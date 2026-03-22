package org.opentmf.mockserver.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * @author Gokhan Demir
 */
public final class JacksonUtil {

  private static final JsonMapper OBJECT_MAPPER = JsonMapper.builder()
      .changeDefaultPropertyInclusion(v -> v.withValueInclusion(JsonInclude.Include.NON_NULL))
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      .addModule(new SimpleModule("OffsetDateTimeModule")
          .addDeserializer(OffsetDateTime.class, new DelegatingDateTimeDeserializer()))
      .build();

  private JacksonUtil() {
  }

  public static JsonNode readAsTree(String json) {
    try {
      return OBJECT_MAPPER.readTree(json);
    } catch (JacksonException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public static <T> T convertValue(Object object, Class<T> valueType) {
    return OBJECT_MAPPER.convertValue(object, valueType);
  }

  public static String writeAsString(Object obj) {
    try {
      return OBJECT_MAPPER.writeValueAsString(obj);
    } catch (JacksonException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public static List<JsonNode> convertToJsonNodeList(List<Object> list) {
    return OBJECT_MAPPER.convertValue(list, new TypeReference<List<JsonNode>>() {
    });
  }

  public static ArrayNode createArrayNode() {
    return OBJECT_MAPPER.createArrayNode();
  }

  public static ObjectNode createObjectNode() {
    return OBJECT_MAPPER.createObjectNode();
  }

  static class DelegatingDateTimeDeserializer
      extends tools.jackson.databind.ValueDeserializer<OffsetDateTime> {

    @Override
    public OffsetDateTime deserialize(
        tools.jackson.core.JsonParser p,
        tools.jackson.databind.DeserializationContext context) {
      String text = p.getText();
      try {
        return OffsetDateTime.parse(text);
      } catch (Exception e) {
        return LocalDateTime.parse(text).atOffset(ZoneOffset.UTC);
      }
    }
  }
}
