package org.mockserver.serialization.serializers.collections;

import static org.mockserver.model.NottableString.serialiseNottableString;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import org.mockserver.model.KeyMatchStyle;
import org.mockserver.model.KeyToMultiValue;
import org.mockserver.model.KeysToMultiValues;
import org.mockserver.model.NottableString;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * @author jamesdbloom
 */
public abstract class KeysToMultiValuesSerializer<T extends KeysToMultiValues<? extends KeyToMultiValue, T>> extends StdSerializer<T> {

    KeysToMultiValuesSerializer(Class<T> valueClass) {
        super(valueClass);
    }

    @Override
    public void serialize(T collection, JsonGenerator jgen, SerializationContext provider) {
        jgen.writeStartObject();
        if (collection.getKeyMatchStyle() != null && collection.getKeyMatchStyle() != KeyMatchStyle.SUB_SET) {
            jgen.writePOJOProperty("keyMatchStyle", collection.getKeyMatchStyle());
        }
        ArrayList<NottableString> keys = new ArrayList<>(collection.keySet());
        Collections.sort(keys);
        for (NottableString key : keys) {
            jgen.writeName(serialiseNottableString(key));
            if (key.getParameterStyle() != null) {
                jgen.writeStartObject();
                jgen.writePOJOProperty("parameterStyle", key.getParameterStyle());
                jgen.writeName("values");
                writeValuesArray(collection, jgen, key);
                jgen.writeEndObject();
            } else {
                writeValuesArray(collection, jgen, key);
            }
        }
        jgen.writeEndObject();
    }

    private void writeValuesArray(T collection, JsonGenerator jgen, NottableString key) {
        Collection<NottableString> values = collection.getValues(key);
        jgen.writeStartArray(values, values.size());
        for (NottableString nottableString : values) {
            jgen.writePOJO(nottableString);
        }
        jgen.writeEndArray();
    }

}
