package org.mockserver.serialization.serializers.response;

import org.mockserver.matchers.Times;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * @author jamesdbloom
 */
public class TimesSerializer extends StdSerializer<Times> {

  public TimesSerializer() {
    super(Times.class);
  }

  @Override
  public void serialize(Times times, JsonGenerator jgen, SerializationContext provider) {
    jgen.writeStartObject();
    if (!times.isUnlimited()) {
      jgen.writeNumberProperty("remainingTimes", times.getRemainingTimes());
    } else {
      jgen.writeBooleanProperty("unlimited", times.isUnlimited());
    }
    jgen.writeEndObject();
  }
}
