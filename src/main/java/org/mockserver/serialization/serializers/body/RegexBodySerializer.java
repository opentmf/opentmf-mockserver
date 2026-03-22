package org.mockserver.serialization.serializers.body;

import org.mockserver.model.RegexBody;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * @author jamesdbloom
 */
public class RegexBodySerializer extends StdSerializer<RegexBody> {

    public RegexBodySerializer() {
        super(RegexBody.class);
    }

    @Override
    public void serialize(RegexBody regexBody, JsonGenerator jgen, SerializationContext provider) {
        jgen.writeStartObject();
        if (regexBody.getNot() != null && regexBody.getNot()) {
            jgen.writeBooleanProperty("not", regexBody.getNot());
        }
        if (regexBody.getOptional() != null && regexBody.getOptional()) {
            jgen.writeBooleanProperty("optional", regexBody.getOptional());
        }
        jgen.writeStringProperty("type", regexBody.getType().name());
        jgen.writeStringProperty("regex", regexBody.getValue());
        jgen.writeEndObject();
    }
}
