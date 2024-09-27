package com.pia.mockserver.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.InstantDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.github.fge.jsonpatch.mergepatch.JsonMergePatch;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * @author Gokhan Demir
 */
public final class JacksonUtil {

  private static final JavaTimeModule JAVA_TIME_MODULE = new JavaTimeModule();
  private static final ObjectMapper OBJECT_MAPPER;

  static {
    JAVA_TIME_MODULE.addDeserializer(OffsetDateTime.class, new DelegatingDateTimeDeserializer());

    OBJECT_MAPPER = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .registerModule(JAVA_TIME_MODULE);
  }

  private JacksonUtil() {
  }

  public static JsonNode readAsTree(String json) {
    try {
      return OBJECT_MAPPER.readTree(json);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public static <T> T convertValue(Object object, Class<T> valueType) {
    return OBJECT_MAPPER.convertValue(object, valueType);
  }

  public static JsonMergePatch readAsJsonMerger(String json) {
    try {
      return OBJECT_MAPPER.readValue(json, JsonMergePatch.class);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public static String writeAsString(Object obj) {
    try {
      return OBJECT_MAPPER.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
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

  static class DelegatingDateTimeDeserializer extends JsonDeserializer<OffsetDateTime> {

    @Override
    public OffsetDateTime deserialize(JsonParser p, DeserializationContext context)
        throws IOException {
      try {
        return InstantDeserializer.OFFSET_DATE_TIME.deserialize(p, context);
      } catch (IOException e) {
        return LocalDateTimeDeserializer.INSTANCE.deserialize(p, context).atOffset(ZoneOffset.UTC);
      }
    }
  }
}
