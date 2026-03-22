package org.mockserver.serialization.serializers.condition;

import org.mockserver.serialization.model.VerificationTimesDTO;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * @author jamesdbloom
 */
public class VerificationTimesDTOSerializer extends StdSerializer<VerificationTimesDTO> {

    public VerificationTimesDTOSerializer() {
        super(VerificationTimesDTO.class);
    }

    @Override
    public void serialize(VerificationTimesDTO verificationTimesDTO, JsonGenerator jgen, SerializationContext provider) {
        jgen.writeStartObject();
        if (verificationTimesDTO.getAtLeast() != -1) {
            jgen.writeNumberProperty("atLeast", verificationTimesDTO.getAtLeast());
        }
        if (verificationTimesDTO.getAtMost() != -1) {
            jgen.writeNumberProperty("atMost", verificationTimesDTO.getAtMost());
        }
        jgen.writeEndObject();
    }
}
