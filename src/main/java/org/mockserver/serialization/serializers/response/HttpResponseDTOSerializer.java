package org.mockserver.serialization.serializers.response;

import org.mockserver.serialization.model.*;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * @author jamesdbloom
 */
public class HttpResponseDTOSerializer extends StdSerializer<HttpResponseDTO> {

  public HttpResponseDTOSerializer() {
    super(HttpResponseDTO.class);
  }

  @Override
  public void serialize(
      HttpResponseDTO httpResponseDTO, JsonGenerator jgen, SerializationContext provider) {
    jgen.writeStartObject();
    if (httpResponseDTO.getStatusCode() != null) {
      jgen.writePOJOProperty("statusCode", httpResponseDTO.getStatusCode());
    }
    if (httpResponseDTO.getReasonPhrase() != null) {
      jgen.writePOJOProperty("reasonPhrase", httpResponseDTO.getReasonPhrase());
    }
    if (httpResponseDTO.getHeaders() != null && !httpResponseDTO.getHeaders().isEmpty()) {
      jgen.writePOJOProperty("headers", httpResponseDTO.getHeaders());
    }
    if (httpResponseDTO.getCookies() != null && !httpResponseDTO.getCookies().isEmpty()) {
      jgen.writePOJOProperty("cookies", httpResponseDTO.getCookies());
    }
    BodyWithContentTypeDTO body = httpResponseDTO.getBody();
    if (body != null) {
      if (body instanceof StringBodyDTO && !((StringBodyDTO) body).getString().isEmpty()) {
        jgen.writePOJOProperty("body", body);
      } else if (body instanceof JsonBodyDTO && !((JsonBodyDTO) body).getJson().isEmpty()) {
        jgen.writePOJOProperty("body", body);
        jgen.writePOJOProperty("body", body);
      } else if (body instanceof BinaryBodyDTO) {
        jgen.writePOJOProperty("body", body);
      } else if (body instanceof LogEntryBodyDTO) {
        jgen.writePOJOProperty("body", body);
      }
    }
    if (httpResponseDTO.getDelay() != null) {
      jgen.writePOJOProperty("delay", httpResponseDTO.getDelay());
    }
    if (httpResponseDTO.getConnectionOptions() != null) {
      jgen.writePOJOProperty("connectionOptions", httpResponseDTO.getConnectionOptions());
    }
    jgen.writeEndObject();
  }
}
