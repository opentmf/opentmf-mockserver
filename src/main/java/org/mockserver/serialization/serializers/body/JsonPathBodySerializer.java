package org.mockserver.serialization.serializers.body;

import org.mockserver.model.JsonPathBody;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * @author jamesdbloom
 */
public class JsonPathBodySerializer extends StdSerializer<JsonPathBody> {

    public JsonPathBodySerializer() {
        super(JsonPathBody.class);
    }

    @Override
    public void serialize(JsonPathBody jsonPathBody, JsonGenerator jgen, SerializationContext provider) {
        jgen.writeStartObject();
        if (jsonPathBody.getNot() != null && jsonPathBody.getNot()) {
            jgen.writeBooleanProperty("not", jsonPathBody.getNot());
        }
        if (jsonPathBody.getOptional() != null && jsonPathBody.getOptional()) {
            jgen.writeBooleanProperty("optional", jsonPathBody.getOptional());
        }
        jgen.writeStringProperty("type", jsonPathBody.getType().name());
        jgen.writeStringProperty("jsonPath", jsonPathBody.getValue());
        jgen.writeEndObject();
    }
}
