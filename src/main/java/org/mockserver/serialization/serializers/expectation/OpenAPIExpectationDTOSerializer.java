package org.mockserver.serialization.serializers.expectation;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.OpenAPIExpectationDTO;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * @author jamesdbloom
 */
public class OpenAPIExpectationDTOSerializer extends StdSerializer<OpenAPIExpectationDTO> {

  private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createObjectMapper();

  public OpenAPIExpectationDTOSerializer() {
    super(OpenAPIExpectationDTO.class);
  }

  @Override
  public void serialize(
      OpenAPIExpectationDTO openAPIDefinition, JsonGenerator jgen, SerializationContext provider) {
    jgen.writeStartObject();
    if (isNotBlank(openAPIDefinition.getSpecUrlOrPayload())) {
      if (openAPIDefinition.getSpecUrlOrPayload().trim().startsWith("{")) {
        jgen.writePOJOProperty(
            "specUrlOrPayload", OBJECT_MAPPER.readTree(openAPIDefinition.getSpecUrlOrPayload()));
      } else {
        jgen.writePOJOProperty("specUrlOrPayload", openAPIDefinition.getSpecUrlOrPayload());
      }
    }
    if (openAPIDefinition.getOperationsAndResponses() != null
        && !openAPIDefinition.getOperationsAndResponses().isEmpty()) {
      jgen.writePOJOProperty(
          "operationsAndResponses", openAPIDefinition.getOperationsAndResponses());
    }
    jgen.writeEndObject();
  }
}
