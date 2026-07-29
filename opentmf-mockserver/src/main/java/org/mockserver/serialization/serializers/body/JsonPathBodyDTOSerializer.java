package org.mockserver.serialization.serializers.body;

import org.mockserver.serialization.model.JsonPathBodyDTO;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * @author jamesdbloom
 */
public class JsonPathBodyDTOSerializer extends StdSerializer<JsonPathBodyDTO> {

  public JsonPathBodyDTOSerializer() {
    super(JsonPathBodyDTO.class);
  }

  @Override
  public void serialize(
      JsonPathBodyDTO jsonPathBodyDTO, JsonGenerator jgen, SerializationContext provider) {
    jgen.writeStartObject();
    if (jsonPathBodyDTO.getNot() != null && jsonPathBodyDTO.getNot()) {
      jgen.writeBooleanProperty("not", jsonPathBodyDTO.getNot());
    }
    if (jsonPathBodyDTO.getOptional() != null && jsonPathBodyDTO.getOptional()) {
      jgen.writeBooleanProperty("optional", jsonPathBodyDTO.getOptional());
    }
    jgen.writeStringProperty("type", jsonPathBodyDTO.getType().name());
    jgen.writeStringProperty("jsonPath", jsonPathBodyDTO.getJsonPath());
    jgen.writeEndObject();
  }
}
