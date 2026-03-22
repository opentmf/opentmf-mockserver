package org.mockserver.serialization.serializers.body;

import org.mockserver.model.StringBody;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * @author jamesdbloom
 */
public class StringBodySerializer extends StdSerializer<StringBody> {

    private final boolean serialiseDefaultValues;

    public StringBodySerializer(boolean serialiseDefaultValues) {
        super(StringBody.class);
        this.serialiseDefaultValues = serialiseDefaultValues;
    }

    @Override
    public void serialize(StringBody stringBody, JsonGenerator jgen, SerializationContext provider) {
        boolean notFieldSetAndNotDefault = stringBody.getNot() != null && stringBody.getNot();
        boolean optionalFieldSetAndNotDefault = stringBody.getOptional() != null && stringBody.getOptional();
        boolean subStringFieldNotDefault = stringBody.isSubString();
        boolean contentTypeFieldSet = stringBody.getContentType() != null;
        if (serialiseDefaultValues || notFieldSetAndNotDefault || optionalFieldSetAndNotDefault || contentTypeFieldSet || subStringFieldNotDefault) {
            jgen.writeStartObject();
            if (notFieldSetAndNotDefault) {
                jgen.writeBooleanProperty("not", true);
            }
            if (optionalFieldSetAndNotDefault) {
                jgen.writeBooleanProperty("optional", true);
            }
            jgen.writeStringProperty("type", stringBody.getType().name());
            jgen.writeStringProperty("string", stringBody.getValue());
            if (subStringFieldNotDefault) {
                jgen.writeBooleanProperty("subString", true);
            }
            if (contentTypeFieldSet) {
                jgen.writeStringProperty("contentType", stringBody.getContentType());
            }
            jgen.writeEndObject();
        } else {
            jgen.writeString(stringBody.getValue());
        }
    }
}
