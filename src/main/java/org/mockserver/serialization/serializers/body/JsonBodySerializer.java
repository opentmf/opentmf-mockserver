package org.mockserver.serialization.serializers.body;

import static org.mockserver.log.model.LogEntry.LogMessageType.EXCEPTION;

import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.JsonBody;
import org.mockserver.serialization.ObjectMapperFactory;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * @author jamesdbloom
 */
public class JsonBodySerializer extends StdSerializer<JsonBody> {

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createObjectMapper();
    private final boolean serialiseDefaultValues;

    public JsonBodySerializer(boolean serialiseDefaultValues) {
        super(JsonBody.class);
        this.serialiseDefaultValues = serialiseDefaultValues;
    }

    @Override
    public void serialize(JsonBody jsonBody, JsonGenerator jgen, SerializationContext provider) {
        boolean notNonDefault = jsonBody.getNot() != null && jsonBody.getNot();
        boolean optionalNonDefault = jsonBody.getOptional() != null && jsonBody.getOptional();
        boolean contentTypeNonDefault = jsonBody.getContentType() != null && !jsonBody.getContentType().equals(JsonBody.DEFAULT_JSON_CONTENT_TYPE.toString());
        boolean matchTypeNonDefault = jsonBody.getMatchType() != JsonBody.DEFAULT_MATCH_TYPE;
        if (serialiseDefaultValues || notNonDefault || optionalNonDefault || contentTypeNonDefault || matchTypeNonDefault) {
            jgen.writeStartObject();
            if (notNonDefault) {
                jgen.writeBooleanProperty("not", jsonBody.getNot());
            }
            if (optionalNonDefault) {
                jgen.writeBooleanProperty("optional", jsonBody.getOptional());
            }
            if (contentTypeNonDefault) {
                jgen.writeStringProperty("contentType", jsonBody.getContentType());
            }
            jgen.writeStringProperty("type", jsonBody.getType().name());
            try {
                jgen.writePOJOProperty("json", OBJECT_MAPPER.readTree(jsonBody.getValue()));
            } catch (Throwable throwable) {
                new MockServerLogger().logEvent(
                    new LogEntry()
                        .setType(EXCEPTION)
                        .setMessageFormat("exception:{} while deserialising jsonBody with json:{}")
                        .setArguments(throwable.getMessage(), jsonBody.getValue())
                        .setThrowable(throwable)
                );
            }
            if (matchTypeNonDefault) {
                jgen.writeStringProperty("matchType", jsonBody.getMatchType().name());
            }
            jgen.writeEndObject();
        } else {
            jgen.writePOJO(OBJECT_MAPPER.readTree(jsonBody.getValue()));
        }
    }
}
