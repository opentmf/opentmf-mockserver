package org.mockserver.serialization.serializers.body;

import static org.mockserver.log.model.LogEntry.LogMessageType.EXCEPTION;

import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.JsonBody;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.JsonBodyDTO;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * @author jamesdbloom
 */
public class JsonBodyDTOSerializer extends StdSerializer<JsonBodyDTO> {

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createObjectMapper();
    private final boolean serialiseDefaultValues;

    public JsonBodyDTOSerializer(boolean serialiseDefaultValues) {
        super(JsonBodyDTO.class);
        this.serialiseDefaultValues = serialiseDefaultValues;
    }

    @Override
    public void serialize(JsonBodyDTO jsonBodyDTO, JsonGenerator jgen, SerializationContext provider) {
        boolean notNonDefault = jsonBodyDTO.getNot() != null && jsonBodyDTO.getNot();
        boolean optionalNonDefault = jsonBodyDTO.getOptional() != null && jsonBodyDTO.getOptional();
        boolean contentTypeNonDefault = jsonBodyDTO.getContentType() != null && !jsonBodyDTO.getContentType().equals(JsonBody.DEFAULT_JSON_CONTENT_TYPE.toString());
        boolean matchTypeNonDefault = jsonBodyDTO.getMatchType() != JsonBody.DEFAULT_MATCH_TYPE;
        if (serialiseDefaultValues || notNonDefault || optionalNonDefault || contentTypeNonDefault || matchTypeNonDefault) {
            jgen.writeStartObject();
            if (notNonDefault) {
                jgen.writeBooleanProperty("not", jsonBodyDTO.getNot());
            }
            if (optionalNonDefault) {
                jgen.writeBooleanProperty("optional", jsonBodyDTO.getOptional());
            }
            if (contentTypeNonDefault) {
                jgen.writeStringProperty("contentType", jsonBodyDTO.getContentType());
            }
            jgen.writeStringProperty("type", jsonBodyDTO.getType().name());
            try {
                jgen.writePOJOProperty("json", OBJECT_MAPPER.readTree(jsonBodyDTO.getJson()));
            } catch (Throwable throwable) {
                new MockServerLogger().logEvent(
                    new LogEntry()
                        .setType(EXCEPTION)
                        .setMessageFormat("exception:{} while deserialising JsonBodyDTO with json:{}")
                        .setArguments(throwable.getMessage(), jsonBodyDTO.getJson())
                        .setThrowable(throwable)
                );
            }
            if (jsonBodyDTO.getRawBytes() != null) {
                jgen.writePOJOProperty("rawBytes", jsonBodyDTO.getRawBytes());
            }
            if (matchTypeNonDefault) {
                jgen.writeStringProperty("matchType", jsonBodyDTO.getMatchType().name());
            }
            jgen.writeEndObject();
        } else {
            jgen.writePOJO(OBJECT_MAPPER.readTree(jsonBodyDTO.getJson()));
        }
    }
}
