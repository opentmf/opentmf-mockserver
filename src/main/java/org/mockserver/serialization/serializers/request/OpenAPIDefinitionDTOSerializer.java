package org.mockserver.serialization.serializers.request;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.OpenAPIDefinitionDTO;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * @author jamesdbloom
 */
public class OpenAPIDefinitionDTOSerializer extends StdSerializer<OpenAPIDefinitionDTO> {

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createObjectMapper();

    public OpenAPIDefinitionDTOSerializer() {
        super(OpenAPIDefinitionDTO.class);
    }

    @Override
    public void serialize(OpenAPIDefinitionDTO openAPIDefinition, JsonGenerator jgen, SerializationContext provider) {
        jgen.writeStartObject();
        if (openAPIDefinition.getNot() != null && openAPIDefinition.getNot()) {
            jgen.writeBooleanProperty("not", openAPIDefinition.getNot());
        }
        if (isNotBlank(openAPIDefinition.getOperationId())) {
            jgen.writePOJOProperty("operationId", openAPIDefinition.getOperationId());
        }
        if (isNotBlank(openAPIDefinition.getSpecUrlOrPayload())) {
            if (openAPIDefinition.getSpecUrlOrPayload().trim().startsWith("{")) {
                jgen.writePOJOProperty("specUrlOrPayload", OBJECT_MAPPER.readTree(openAPIDefinition.getSpecUrlOrPayload()));
            } else {
                jgen.writePOJOProperty("specUrlOrPayload", openAPIDefinition.getSpecUrlOrPayload());
            }
        }
        jgen.writeEndObject();
    }
}
