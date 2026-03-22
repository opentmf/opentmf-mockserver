package org.mockserver.serialization.serializers.body;

import org.mockserver.serialization.model.ParameterBodyDTO;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * @author jamesdbloom
 */
public class ParameterBodyDTOSerializer extends StdSerializer<ParameterBodyDTO> {

  public ParameterBodyDTOSerializer() {
    super(ParameterBodyDTO.class);
  }

  @Override
  public void serialize(
      ParameterBodyDTO parameterBodyDTO, JsonGenerator jgen, SerializationContext provider) {
    jgen.writeStartObject();
    if (parameterBodyDTO.getNot() != null && parameterBodyDTO.getNot()) {
      jgen.writeBooleanProperty("not", parameterBodyDTO.getNot());
    }
    if (parameterBodyDTO.getOptional() != null && parameterBodyDTO.getOptional()) {
      jgen.writeBooleanProperty("optional", parameterBodyDTO.getOptional());
    }
    jgen.writeStringProperty("type", parameterBodyDTO.getType().name());
    if (!parameterBodyDTO.getParameters().isEmpty()) {
      jgen.writePOJOProperty("parameters", parameterBodyDTO.getParameters());
    }
    jgen.writeEndObject();
  }
}
