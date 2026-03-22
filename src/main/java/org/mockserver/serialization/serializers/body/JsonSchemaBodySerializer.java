package org.mockserver.serialization.serializers.body;

import org.mockserver.model.JsonSchemaBody;
import org.mockserver.serialization.ObjectMapperFactory;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * @author jamesdbloom
 */
public class JsonSchemaBodySerializer extends StdSerializer<JsonSchemaBody> {

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createObjectMapper();

    public JsonSchemaBodySerializer() {
        super(JsonSchemaBody.class);
    }

    @Override
    public void serialize(JsonSchemaBody jsonSchemaBody, JsonGenerator jgen, SerializationContext provider) {
        jgen.writeStartObject();
        if (jsonSchemaBody.getNot() != null && jsonSchemaBody.getNot()) {
            jgen.writeBooleanProperty("not", jsonSchemaBody.getNot());
        }
        if (jsonSchemaBody.getOptional() != null && jsonSchemaBody.getOptional()) {
            jgen.writeBooleanProperty("optional", jsonSchemaBody.getOptional());
        }
        jgen.writeStringProperty("type", jsonSchemaBody.getType().name());
        jgen.writePOJOProperty("jsonSchema", OBJECT_MAPPER.readTree(jsonSchemaBody.getValue()));
        if (jsonSchemaBody.getParameterStyles() != null) {
            jgen.writePOJOProperty("parameterStyles", jsonSchemaBody.getParameterStyles());
        }
        jgen.writeEndObject();
    }
}
