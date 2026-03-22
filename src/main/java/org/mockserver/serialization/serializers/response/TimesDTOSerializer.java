package org.mockserver.serialization.serializers.response;

import org.mockserver.serialization.model.TimesDTO;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * @author jamesdbloom
 */
public class TimesDTOSerializer extends StdSerializer<TimesDTO> {

    public TimesDTOSerializer() {
        super(TimesDTO.class);
    }

    @Override
    public void serialize(TimesDTO times, JsonGenerator jgen, SerializationContext provider) {
        jgen.writeStartObject();
        if (!times.isUnlimited()) {
            jgen.writeNumberProperty("remainingTimes", times.getRemainingTimes());
        } else {
            jgen.writeBooleanProperty("unlimited", times.isUnlimited());
        }
        jgen.writeEndObject();
    }
}
