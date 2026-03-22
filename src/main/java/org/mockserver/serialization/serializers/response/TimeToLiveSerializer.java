package org.mockserver.serialization.serializers.response;

import org.mockserver.matchers.TimeToLive;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * @author jamesdbloom
 */
public class TimeToLiveSerializer extends StdSerializer<TimeToLive> {

  public TimeToLiveSerializer() {
    super(TimeToLive.class);
  }

  @Override
  public void serialize(TimeToLive timeToLive, JsonGenerator jgen, SerializationContext provider) {
    jgen.writeStartObject();
    if (!timeToLive.isUnlimited()) {
      jgen.writePOJOProperty("timeUnit", timeToLive.getTimeUnit());
      jgen.writeNumberProperty("timeToLive", timeToLive.getTimeToLive());
      jgen.writeNumberProperty("endDate", timeToLive.getEndDate());
    } else {
      jgen.writeBooleanProperty("unlimited", timeToLive.isUnlimited());
    }
    jgen.writeEndObject();
  }
}
