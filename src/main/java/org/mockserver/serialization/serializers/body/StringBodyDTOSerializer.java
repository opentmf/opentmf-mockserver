package org.mockserver.serialization.serializers.body;

import org.mockserver.serialization.model.StringBodyDTO;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * @author jamesdbloom
 */
public class StringBodyDTOSerializer extends StdSerializer<StringBodyDTO> {

  private final boolean serialiseDefaultValues;

  public StringBodyDTOSerializer(boolean serialiseDefaultValues) {
    super(StringBodyDTO.class);
    this.serialiseDefaultValues = serialiseDefaultValues;
  }

  @Override
  public void serialize(
      StringBodyDTO stringBodyDTO, JsonGenerator jgen, SerializationContext provider) {
    boolean notFieldSetAndNotDefault = stringBodyDTO.getNot() != null && stringBodyDTO.getNot();
    boolean optionalFieldSetAndNotDefault =
        stringBodyDTO.getOptional() != null && stringBodyDTO.getOptional();
    boolean subStringFieldNotDefault = stringBodyDTO.isSubString();
    boolean contentTypeFieldSet = stringBodyDTO.getContentType() != null;
    if (serialiseDefaultValues
        || notFieldSetAndNotDefault
        || optionalFieldSetAndNotDefault
        || contentTypeFieldSet
        || subStringFieldNotDefault) {
      jgen.writeStartObject();
      if (notFieldSetAndNotDefault) {
        jgen.writeBooleanProperty("not", true);
      }
      if (optionalFieldSetAndNotDefault) {
        jgen.writeBooleanProperty("optional", true);
      }
      jgen.writeStringProperty("type", stringBodyDTO.getType().name());
      jgen.writeStringProperty("string", stringBodyDTO.getString());
      if (stringBodyDTO.getRawBytes() != null) {
        jgen.writePOJOProperty("rawBytes", stringBodyDTO.getRawBytes());
      }
      if (subStringFieldNotDefault) {
        jgen.writeBooleanProperty("subString", true);
      }
      if (contentTypeFieldSet) {
        jgen.writeStringProperty("contentType", stringBodyDTO.getContentType());
      }
      jgen.writeEndObject();
    } else {
      jgen.writeString(stringBodyDTO.getString());
    }
  }
}
