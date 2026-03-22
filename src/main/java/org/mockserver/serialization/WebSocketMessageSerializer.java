package org.mockserver.serialization;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpRequestAndHttpResponse;
import org.mockserver.model.HttpResponse;
import org.mockserver.serialization.model.WebSocketMessageDTO;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;

/**
 * @author jamesdbloom
 */
@SuppressWarnings({"rawtypes", "unchecked", "FieldMayBeFinal"})
public class WebSocketMessageSerializer {

    private ObjectWriter objectWriter = ObjectMapperFactory.createObjectMapper(true, false);
    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
    private Map<Class, Serializer> serializers;

    public WebSocketMessageSerializer(MockServerLogger mockServerLogger) {
        serializers = ImmutableMap.of(
            HttpRequest.class, new HttpRequestSerializer(mockServerLogger),
            HttpResponse.class, new HttpResponseSerializer(mockServerLogger),
            HttpRequestAndHttpResponse.class, new HttpRequestAndHttpResponseSerializer(mockServerLogger)
        );
    }

    public String serialize(Object message) throws JacksonException {
        if (serializers.containsKey(message.getClass())) {
            WebSocketMessageDTO value = new WebSocketMessageDTO().setType(message.getClass().getName()).setValue(serializers.get(message.getClass()).serialize((message)));
            return objectWriter.writeValueAsString(value);
        } else {
            return objectWriter.writeValueAsString(new WebSocketMessageDTO().setType(message.getClass().getName()).setValue(objectMapper.writeValueAsString(message)));
        }
    }

    public Object deserialize(String messageJson) throws ClassNotFoundException, Exception {
        WebSocketMessageDTO webSocketMessageDTO = objectMapper.readValue(messageJson, WebSocketMessageDTO.class);
        if (webSocketMessageDTO.getType() != null && webSocketMessageDTO.getValue() != null) {
            Class format = Class.forName(webSocketMessageDTO.getType());
            if (serializers.containsKey(format)) {
                return serializers.get(format).deserialize(webSocketMessageDTO.getValue());
            } else {
                return objectMapper.readValue(webSocketMessageDTO.getValue(), format);
            }
        } else {
            return null;
        }
    }
}
