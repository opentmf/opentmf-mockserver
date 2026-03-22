package org.mockserver.serialization.serializers.collections;

import static org.mockserver.model.NottableString.serialiseNottableString;

import org.mockserver.model.Cookie;
import org.mockserver.model.Cookies;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * @author jamesdbloom
 */
public class CookiesSerializer extends StdSerializer<Cookies> {

    public CookiesSerializer() {
        super(Cookies.class);
    }

    @Override
    public void serialize(Cookies collection, JsonGenerator jgen, SerializationContext provider) {
        jgen.writeStartObject();
        for (Cookie cookie : collection.getEntries()) {
            jgen.writePOJOProperty(serialiseNottableString(cookie.getName()), cookie.getValue());
        }
        jgen.writeEndObject();
    }

}
