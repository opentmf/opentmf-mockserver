package org.mockserver.serialization.serializers.expectation;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import org.mockserver.mock.OpenAPIExpectation;
import org.mockserver.serialization.ObjectMapperFactory;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * @author jamesdbloom
 */
public class OpenAPIExpectationSerializer extends StdSerializer<OpenAPIExpectation> {

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createObjectMapper();

    public OpenAPIExpectationSerializer() {
        super(OpenAPIExpectation.class);
    }

    @Override
    public void serialize(OpenAPIExpectation openAPIDefinition, JsonGenerator jgen, SerializationContext provider) {
        jgen.writeStartObject();
        if (isNotBlank(openAPIDefinition.getSpecUrlOrPayload())) {
            if (openAPIDefinition.getSpecUrlOrPayload().trim().startsWith("{")) {
                jgen.writePOJOProperty("specUrlOrPayload", OBJECT_MAPPER.readTree(openAPIDefinition.getSpecUrlOrPayload()));
            } else {
                jgen.writePOJOProperty("specUrlOrPayload", openAPIDefinition.getSpecUrlOrPayload());
            }
        }
        if (openAPIDefinition.getOperationsAndResponses() != null && !openAPIDefinition.getOperationsAndResponses().isEmpty()) {
            jgen.writePOJOProperty("operationsAndResponses", openAPIDefinition.getOperationsAndResponses());
        }
        jgen.writeEndObject();
    }
}
