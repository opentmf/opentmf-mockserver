package org.mockserver.serialization.serializers.response;

import org.mockserver.model.*;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * @author jamesdbloom
 */
public class HttpResponseSerializer extends StdSerializer<HttpResponse> {

  public HttpResponseSerializer() {
    super(HttpResponse.class);
  }

  @Override
  public void serialize(
      HttpResponse httpResponse, JsonGenerator jgen, SerializationContext provider) {
    jgen.writeStartObject();
    if (httpResponse.getStatusCode() != null) {
      jgen.writePOJOProperty("statusCode", httpResponse.getStatusCode());
    }
    if (httpResponse.getReasonPhrase() != null) {
      jgen.writePOJOProperty("reasonPhrase", httpResponse.getReasonPhrase());
    }
    if (httpResponse.getHeaderList() != null && !httpResponse.getHeaderList().isEmpty()) {
      jgen.writePOJOProperty("headers", httpResponse.getHeaders());
    }
    if (httpResponse.getCookieList() != null && !httpResponse.getCookieList().isEmpty()) {
      jgen.writePOJOProperty("cookies", httpResponse.getCookies());
    }
    Body<?> body = httpResponse.getBody();
    if (body != null) {
      if (body instanceof StringBody && !((StringBody) body).getValue().isEmpty()) {
        jgen.writePOJOProperty("body", body);
      } else if (body instanceof JsonBody && !((JsonBody) body).getValue().isEmpty()) {
        jgen.writePOJOProperty("body", body);
      } else if (body instanceof BinaryBody && ((BinaryBody) body).getValue().length > 0) {
        jgen.writePOJOProperty("body", body);
      } else if (body instanceof ParameterBody && !((ParameterBody) body).getValue().isEmpty()) {
        jgen.writePOJOProperty("body", body);
        jgen.writePOJOProperty("body", body);
      } else if (body instanceof LogEntryBody) {
        jgen.writePOJOProperty("body", body);
      }
    }
    if (httpResponse.getDelay() != null) {
      jgen.writePOJOProperty("delay", httpResponse.getDelay());
    }
    if (httpResponse.getConnectionOptions() != null) {
      jgen.writePOJOProperty("connectionOptions", httpResponse.getConnectionOptions());
    }
    jgen.writeEndObject();
  }
}
