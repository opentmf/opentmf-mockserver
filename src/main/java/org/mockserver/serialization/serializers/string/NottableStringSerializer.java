package org.mockserver.serialization.serializers.string;

import static org.mockserver.model.NottableString.serialiseNottableString;

import org.mockserver.model.NottableSchemaString;
import org.mockserver.model.NottableString;
import org.mockserver.serialization.ObjectMapperFactory;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * @author jamesdbloom
 */
public class NottableStringSerializer extends StdSerializer<NottableString> {

  private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createObjectMapper();

  public NottableStringSerializer() {
    super(NottableString.class);
  }

  @Override
  public void serialize(
      NottableString nottableString, JsonGenerator jgen, SerializationContext provider) {
    if (nottableString instanceof NottableSchemaString) {
      writeObject(
          nottableString, jgen, "schema", OBJECT_MAPPER.readTree(nottableString.getValue()));
    } else if (nottableString.getParameterStyle() != null) {
      writeObject(nottableString, jgen, "value", nottableString.getValue());
    } else {
      jgen.writeString(serialiseNottableString(nottableString));
    }
  }

  private void writeObject(
      NottableString nottableString, JsonGenerator jgen, String valueFieldName, Object value) {
    jgen.writeStartObject();
    if (Boolean.TRUE.equals(nottableString.isNot())) {
      jgen.writeBooleanProperty("not", true);
    }
    if (Boolean.TRUE.equals(nottableString.isOptional())) {
      jgen.writeBooleanProperty("optional", true);
    }
    if (nottableString.getParameterStyle() != null) {
      jgen.writePOJOProperty("parameterStyle", nottableString.getParameterStyle());
    }
    jgen.writePOJOProperty(valueFieldName, value);
    jgen.writeEndObject();
  }
}
