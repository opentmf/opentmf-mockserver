package org.mockserver.serialization.serializers.body;

import org.mockserver.serialization.model.LogEntryBodyDTO;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * @author jamesdbloom
 */
public class LogEntryBodyDTOSerializer extends StdSerializer<LogEntryBodyDTO> {

  public LogEntryBodyDTOSerializer() {
    super(LogEntryBodyDTO.class);
  }

  @Override
  public void serialize(
      LogEntryBodyDTO logEventBody, JsonGenerator jgen, SerializationContext provider) {
    jgen.writePOJO(logEventBody.getValue());
  }
}
