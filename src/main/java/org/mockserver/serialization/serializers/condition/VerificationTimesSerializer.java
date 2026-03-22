package org.mockserver.serialization.serializers.condition;

import org.mockserver.verify.VerificationTimes;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * @author jamesdbloom
 */
public class VerificationTimesSerializer extends StdSerializer<VerificationTimes> {

    public VerificationTimesSerializer() {
        super(VerificationTimes.class);
    }

    @Override
    public void serialize(VerificationTimes verificationTimesDTO, JsonGenerator jgen, SerializationContext provider) {
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
