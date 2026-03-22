package org.mockserver.serialization.serializers.request;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import org.mockserver.serialization.model.HttpRequestDTO;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * @author jamesdbloom
 */
public class HttpRequestDTOSerializer extends StdSerializer<HttpRequestDTO> {

    public HttpRequestDTOSerializer() {
        super(HttpRequestDTO.class);
    }

    @Override
    public void serialize(HttpRequestDTO httpRequest, JsonGenerator jgen, SerializationContext provider) {
        jgen.writeStartObject();
        if (httpRequest.getNot() != null && httpRequest.getNot()) {
            jgen.writeBooleanProperty("not", httpRequest.getNot());
        }
        if (httpRequest.getMethod() != null && isNotBlank(httpRequest.getMethod().getValue())) {
            jgen.writePOJOProperty("method", httpRequest.getMethod());
        }
        if (httpRequest.getPath() != null && isNotBlank(httpRequest.getPath().getValue())) {
            jgen.writePOJOProperty("path", httpRequest.getPath());
        }
        if (httpRequest.getPathParameters() != null && !httpRequest.getPathParameters().isEmpty()) {
            jgen.writePOJOProperty("pathParameters", httpRequest.getPathParameters());
        }
        if (httpRequest.getQueryStringParameters() != null && !httpRequest.getQueryStringParameters().isEmpty()) {
            jgen.writePOJOProperty("queryStringParameters", httpRequest.getQueryStringParameters());
        }
        if (httpRequest.getHeaders() != null && !httpRequest.getHeaders().isEmpty()) {
            jgen.writePOJOProperty("headers", httpRequest.getHeaders());
        }
        if (httpRequest.getCookies() != null && !httpRequest.getCookies().isEmpty()) {
            jgen.writePOJOProperty("cookies", httpRequest.getCookies());
        }
        if (httpRequest.getKeepAlive() != null) {
            jgen.writeBooleanProperty("keepAlive", httpRequest.getKeepAlive());
        }
        if (httpRequest.getSecure() != null) {
            jgen.writeBooleanProperty("secure", httpRequest.getSecure());
        }
        if (httpRequest.getClientCertificateChain() != null && !httpRequest.getClientCertificateChain().isEmpty()) {
            jgen.writePOJOProperty("clientCertificateChain", httpRequest.getClientCertificateChain());
        }
        if (httpRequest.getSocketAddress() != null) {
            jgen.writePOJOProperty("socketAddress", httpRequest.getSocketAddress());
        }
        if (httpRequest.getProtocol() != null) {
            jgen.writeStringProperty("protocol", httpRequest.getProtocol().name());
        }
        if (isNotBlank(httpRequest.getLocalAddress())) {
            jgen.writePOJOProperty("localAddress", httpRequest.getLocalAddress());
        }
        if (isNotBlank(httpRequest.getRemoteAddress())) {
            jgen.writePOJOProperty("remoteAddress", httpRequest.getRemoteAddress());
        }
        if (httpRequest.getBody() != null) {
            jgen.writePOJOProperty("body", httpRequest.getBody());
        }
        jgen.writeEndObject();
    }
}
