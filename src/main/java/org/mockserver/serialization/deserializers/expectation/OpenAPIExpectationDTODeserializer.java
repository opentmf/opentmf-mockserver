package org.mockserver.serialization.deserializers.expectation;

import java.util.HashMap;
import java.util.Map;
import org.mockserver.serialization.model.OpenAPIExpectationDTO;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.deser.std.StdDeserializer;

public class OpenAPIExpectationDTODeserializer extends StdDeserializer<OpenAPIExpectationDTO> {

    public OpenAPIExpectationDTODeserializer() {
        super(OpenAPIExpectationDTO.class);
    }

    @Override
    public OpenAPIExpectationDTO deserialize(JsonParser jsonParser, DeserializationContext ctxt) {
        if (jsonParser.currentToken() == JsonToken.START_OBJECT) {
            String specUrlOrPayload = null;
            Map<String, String> operationsAndResponses = null;
            while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = jsonParser.currentName();
                switch (fieldName) {
                    case "specUrlOrPayload":
                        jsonParser.nextToken();
                        JsonNode specUrlOrPayloadField = ctxt.readValue(jsonParser, JsonNode.class);
                        if (specUrlOrPayloadField.isTextual()) {
                            specUrlOrPayload = specUrlOrPayloadField.asText();
                        } else {
                            specUrlOrPayload = specUrlOrPayloadField.toPrettyString();
                        }
                        break;
                    case "operationsAndResponses":
                        jsonParser.nextToken();
                        Map<String, String> value = new HashMap<>();
                        Map<?, ?> map = ctxt.readValue(jsonParser, Map.class);
                        map.keySet().forEach(key -> {
                            if (key instanceof String && map.get(key) instanceof String) {
                                value.put((String) key, (String) map.get(key));
                            }
                        });
                        if (!value.isEmpty()) {
                            operationsAndResponses = value;
                        }
                        break;
                }
            }
            return new OpenAPIExpectationDTO()
                .setSpecUrlOrPayload(specUrlOrPayload)
                .setOperationsAndResponses(operationsAndResponses);
        }
        return null;
    }
}
