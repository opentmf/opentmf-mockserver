package org.mockserver.serialization.serializers.body;

import org.mockserver.model.BinaryBody;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * @author jamesdbloom
 */
public class BinaryBodySerializer extends StdSerializer<BinaryBody> {

  public BinaryBodySerializer() {
    super(BinaryBody.class);
  }

  @Override
  public void serialize(BinaryBody binaryBody, JsonGenerator jgen, SerializationContext provider) {
    jgen.writeStartObject();
    if (binaryBody.getNot() != null && binaryBody.getNot()) {
      jgen.writeBooleanProperty("not", binaryBody.getNot());
    }
    if (binaryBody.getOptional() != null && binaryBody.getOptional()) {
      jgen.writeBooleanProperty("optional", binaryBody.getOptional());
    }
    if (binaryBody.getContentType() != null) {
      jgen.writeStringProperty("contentType", binaryBody.getContentType());
    }
    jgen.writeStringProperty("type", binaryBody.getType().name());
    jgen.writeStringProperty("base64Bytes", binaryBody.toString());
    jgen.writeEndObject();
  }
}
