package org.mockserver.serialization.serializers.body;

import org.mockserver.model.ParameterBody;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * @author jamesdbloom
 */
public class ParameterBodySerializer extends StdSerializer<ParameterBody> {

  public ParameterBodySerializer() {
    super(ParameterBody.class);
  }

  @Override
  public void serialize(
      ParameterBody parameterBody, JsonGenerator jgen, SerializationContext provider) {
    jgen.writeStartObject();
    if (parameterBody.getNot() != null && parameterBody.getNot()) {
      jgen.writeBooleanProperty("not", parameterBody.getNot());
    }
    if (parameterBody.getOptional() != null && parameterBody.getOptional()) {
      jgen.writeBooleanProperty("optional", parameterBody.getOptional());
    }
    jgen.writeStringProperty("type", parameterBody.getType().name());
    if (!parameterBody.getValue().isEmpty()) {
      jgen.writePOJOProperty("parameters", parameterBody.getValue());
    }
    jgen.writeEndObject();
  }
}
