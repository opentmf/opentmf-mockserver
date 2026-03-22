package org.mockserver.serialization.serializers.body;

import org.mockserver.serialization.model.BinaryBodyDTO;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * @author jamesdbloom
 */
public class BinaryBodyDTOSerializer extends StdSerializer<BinaryBodyDTO> {

    public BinaryBodyDTOSerializer() {
        super(BinaryBodyDTO.class);
    }

    @Override
    public void serialize(BinaryBodyDTO binaryBodyDTO, JsonGenerator jgen, SerializationContext provider) {
        jgen.writeStartObject();
        if (binaryBodyDTO.getNot() != null && binaryBodyDTO.getNot()) {
            jgen.writeBooleanProperty("not", binaryBodyDTO.getNot());
        }
        if (binaryBodyDTO.getOptional() != null && binaryBodyDTO.getOptional()) {
            jgen.writeBooleanProperty("optional", binaryBodyDTO.getOptional());
        }
        jgen.writeStringProperty("type", binaryBodyDTO.getType().name());
        jgen.writePOJOProperty("base64Bytes", binaryBodyDTO.getBase64Bytes());
        if (binaryBodyDTO.getContentType() != null) {
            jgen.writeStringProperty("contentType", binaryBodyDTO.getContentType());
        }
        jgen.writeEndObject();
    }
}
