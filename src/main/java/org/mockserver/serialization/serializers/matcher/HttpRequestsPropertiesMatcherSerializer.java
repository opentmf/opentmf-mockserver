package org.mockserver.serialization.serializers.matcher;

import org.mockserver.matchers.HttpRequestPropertiesMatcher;
import org.mockserver.matchers.HttpRequestsPropertiesMatcher;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * @author jamesdbloom
 */
public class HttpRequestsPropertiesMatcherSerializer extends StdSerializer<HttpRequestsPropertiesMatcher> {

    public HttpRequestsPropertiesMatcherSerializer() {
        super(HttpRequestsPropertiesMatcher.class);
    }

    @Override
    public void serialize(HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher, JsonGenerator jgen, SerializationContext provider) {
        jgen.writeStartArray();
        if (httpRequestsPropertiesMatcher.getHttpRequestPropertiesMatchers() != null) {
            for (HttpRequestPropertiesMatcher httpRequestPropertiesMatcher : httpRequestsPropertiesMatcher.getHttpRequestPropertiesMatchers()) {
                jgen.writePOJO(httpRequestPropertiesMatcher);
            }
        }
        jgen.writeEndArray();
    }

}
