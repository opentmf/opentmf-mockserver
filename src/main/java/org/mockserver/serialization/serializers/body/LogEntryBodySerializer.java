package org.mockserver.serialization.serializers.body;

import org.mockserver.model.LogEntryBody;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * @author jamesdbloom
 */
public class LogEntryBodySerializer extends StdSerializer<LogEntryBody> {

    public LogEntryBodySerializer() {
        super(LogEntryBody.class);
    }

    @Override
    public void serialize(LogEntryBody logEventBody, JsonGenerator jgen, SerializationContext provider) {
        jgen.writePOJO(logEventBody.getValue());
    }
}
