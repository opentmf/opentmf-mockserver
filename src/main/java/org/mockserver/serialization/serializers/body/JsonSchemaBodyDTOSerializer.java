package org.mockserver.serialization.serializers.body;

import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.JsonSchemaBodyDTO;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * @author jamesdbloom
 */
public class JsonSchemaBodyDTOSerializer extends StdSerializer<JsonSchemaBodyDTO> {

  private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createObjectMapper();

  public JsonSchemaBodyDTOSerializer() {
    super(JsonSchemaBodyDTO.class);
  }

  @Override
  public void serialize(
      JsonSchemaBodyDTO jsonSchemaBodyDTO, JsonGenerator jgen, SerializationContext provider) {
    jgen.writeStartObject();
    if (jsonSchemaBodyDTO.getNot() != null && jsonSchemaBodyDTO.getNot()) {
      jgen.writeBooleanProperty("not", jsonSchemaBodyDTO.getNot());
    }
    if (jsonSchemaBodyDTO.getOptional() != null && jsonSchemaBodyDTO.getOptional()) {
      jgen.writeBooleanProperty("optional", jsonSchemaBodyDTO.getOptional());
    }
    jgen.writeStringProperty("type", jsonSchemaBodyDTO.getType().name());
    jgen.writePOJOProperty("jsonSchema", OBJECT_MAPPER.readTree(jsonSchemaBodyDTO.getJson()));
    if (jsonSchemaBodyDTO.getParameterStyles() != null) {
      jgen.writePOJOProperty("parameterStyles", jsonSchemaBodyDTO.getParameterStyles());
    }
    jgen.writeEndObject();
  }
}
