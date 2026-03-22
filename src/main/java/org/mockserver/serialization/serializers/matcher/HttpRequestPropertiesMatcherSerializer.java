package org.mockserver.serialization.serializers.matcher;

import org.mockserver.matchers.HttpRequestPropertiesMatcher;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * @author jamesdbloom
 */
public class HttpRequestPropertiesMatcherSerializer
    extends StdSerializer<HttpRequestPropertiesMatcher> {

  public HttpRequestPropertiesMatcherSerializer() {
    super(HttpRequestPropertiesMatcher.class);
  }

  @Override
  public void serialize(
      HttpRequestPropertiesMatcher requestPropertiesMatcher,
      JsonGenerator jgen,
      SerializationContext provider) {
    if (requestPropertiesMatcher.getHttpRequest() != null) {
      jgen.writePOJO(requestPropertiesMatcher.getHttpRequest());
    }
  }
}
