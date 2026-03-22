package org.mockserver.serialization.serializers.log;

import static org.mockserver.character.Character.NEW_LINE;

import org.mockserver.log.model.LogEntry;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * @author jamesdbloom
 */
public class LogEntrySerializer extends StdSerializer<LogEntry> {

    public LogEntrySerializer() {
        super(LogEntry.class);
    }

    @Override
    public void serialize(LogEntry logEntry, JsonGenerator jgen, SerializationContext provider) {
        jgen.writeStartObject();
        if (logEntry.getLogLevel() != null) {
            jgen.writePOJOProperty("logLevel", logEntry.getLogLevel());
        }
        if (logEntry.getTimestamp() != null) {
            jgen.writePOJOProperty("timestamp", logEntry.getTimestamp());
        }
        if (logEntry.getType() != null) {
            jgen.writePOJOProperty("type", logEntry.getType());
        }
        if (logEntry.getHttpRequests() != null) {
            if (logEntry.getHttpRequests().length > 1) {
                jgen.writePOJOProperty("httpRequests", logEntry.getHttpUpdatedRequests());
            } else if (logEntry.getHttpRequests().length == 1) {
                jgen.writePOJOProperty("httpRequest", logEntry.getHttpUpdatedRequests()[0]);
            }
        }
        if (logEntry.getHttpResponse() != null) {
            jgen.writePOJOProperty("httpResponse", logEntry.getHttpUpdatedResponse());
        }
        if (logEntry.getHttpError() != null) {
            jgen.writePOJOProperty("httpError", logEntry.getHttpError());
        }
        if (logEntry.getExpectation() != null) {
            jgen.writePOJOProperty("expectation", logEntry.getExpectation());
        }
        if (logEntry.getMessage() != null) {
            jgen.writePOJOProperty("message", logEntry.getMessage().replaceAll(" {2}", "   ").split(NEW_LINE));
        }
        if (logEntry.getThrowable() != null) {
            jgen.writePOJOProperty("throwable", logEntry.getThrowable());
        }
        jgen.writeEndObject();
    }
}
