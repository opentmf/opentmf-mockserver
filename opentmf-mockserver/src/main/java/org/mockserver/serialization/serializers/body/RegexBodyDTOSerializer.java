package org.mockserver.serialization.serializers.body;

import org.mockserver.serialization.model.RegexBodyDTO;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * @author jamesdbloom
 */
public class RegexBodyDTOSerializer extends StdSerializer<RegexBodyDTO> {

  public RegexBodyDTOSerializer() {
    super(RegexBodyDTO.class);
  }

  @Override
  public void serialize(
      RegexBodyDTO regexBodyDTO, JsonGenerator jgen, SerializationContext provider) {
    jgen.writeStartObject();
    if (regexBodyDTO.getNot() != null && regexBodyDTO.getNot()) {
      jgen.writeBooleanProperty("not", regexBodyDTO.getNot());
    }
    if (regexBodyDTO.getOptional() != null && regexBodyDTO.getOptional()) {
      jgen.writeBooleanProperty("optional", regexBodyDTO.getOptional());
    }
    jgen.writeStringProperty("type", regexBodyDTO.getType().name());
    jgen.writeStringProperty("regex", regexBodyDTO.getRegex());
    jgen.writeEndObject();
  }
}
