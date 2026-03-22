package org.mockserver.serialization.deserializers.string;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.model.NottableOptionalString.optional;
import static org.mockserver.model.NottableSchemaString.schemaString;
import static org.mockserver.model.NottableString.string;

import org.mockserver.model.NottableString;
import org.mockserver.model.ParameterStyle;
import org.mockserver.serialization.ObjectMapperFactory;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.deser.std.StdDeserializer;

/**
 * @author jamesdbloom
 */
public class NottableStringDeserializer extends StdDeserializer<NottableString> {

  private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createObjectMapper();

  public NottableStringDeserializer() {
    super(NottableString.class);
  }

  @Override
  public NottableString deserialize(JsonParser jsonParser, DeserializationContext ctxt) {
    if (jsonParser.currentToken() == JsonToken.START_OBJECT) {
      Boolean not = null;
      Boolean optional = null;
      String value = null;
      JsonNode schema = null;
      ParameterStyle parameterStyle = null;

      while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
        String fieldName = jsonParser.currentName();
        if ("not".equals(fieldName)) {
          jsonParser.nextToken();
          not = jsonParser.getBooleanValue();
        } else if ("optional".equals(fieldName)) {
          jsonParser.nextToken();
          optional = jsonParser.getBooleanValue();
        } else if ("value".equals(fieldName)) {
          jsonParser.nextToken();
          value = ctxt.readValue(jsonParser, String.class);
        } else if ("schema".equals(fieldName)) {
          jsonParser.nextToken();
          schema = ctxt.readValue(jsonParser, JsonNode.class);
        } else if ("parameterStyle".equals(fieldName)) {
          jsonParser.nextToken();
          parameterStyle = ctxt.readValue(jsonParser, ParameterStyle.class);
        }
      }

      NottableString result = null;
      if (schema != null) {
        result = schemaString(schema.toPrettyString(), not);
      } else if (Boolean.TRUE.equals(optional)) {
        result = optional(value, not);
      } else if (isNotBlank(value)) {
        result = string(value, not);
      }

      if (result != null && parameterStyle != null) {
        result.withStyle(parameterStyle);
      }

      return result;
    } else if (jsonParser.currentToken() == JsonToken.VALUE_STRING
        || jsonParser.currentToken() == JsonToken.PROPERTY_NAME) {
      return string(ctxt.readValue(jsonParser, String.class));
    }
    return null;
  }
}
