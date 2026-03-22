package org.mockserver.serialization.serializers.response;

import org.mockserver.serialization.model.TimeToLiveDTO;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * @author jamesdbloom
 */
public class TimeToLiveDTOSerializer extends StdSerializer<TimeToLiveDTO> {

    public TimeToLiveDTOSerializer() {
        super(TimeToLiveDTO.class);
    }

    @Override
    public void serialize(TimeToLiveDTO timeToLive, JsonGenerator jgen, SerializationContext provider) {
        jgen.writeStartObject();
        if (!timeToLive.isUnlimited()) {
            jgen.writePOJOProperty("timeUnit", timeToLive.getTimeUnit());
            jgen.writeNumberProperty("timeToLive", timeToLive.getTimeToLive());
        } else {
            jgen.writeBooleanProperty("unlimited", timeToLive.isUnlimited());
        }
        jgen.writeEndObject();
    }
}
