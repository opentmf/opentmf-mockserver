package org.mockserver.serialization.serializers.request;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import org.mockserver.model.HttpRequest;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * @author jamesdbloom
 */
public class HttpRequestSerializer extends StdSerializer<HttpRequest> {

    public HttpRequestSerializer() {
        super(HttpRequest.class);
    }

    @Override
    public void serialize(HttpRequest httpRequest, JsonGenerator jgen, SerializationContext provider) {
        jgen.writeStartObject();
        if (httpRequest.getNot() != null && httpRequest.getNot()) {
            jgen.writeBooleanProperty("not", httpRequest.getNot());
        }
        if (httpRequest.getMethod() != null && !httpRequest.getMethod().isBlank()) {
            jgen.writePOJOProperty("method", httpRequest.getMethod());
        }
        if (httpRequest.getPath() != null && !httpRequest.getPath().isBlank()) {
            jgen.writePOJOProperty("path", httpRequest.getPath());
        }
        if (httpRequest.getPathParameters() != null && !httpRequest.getPathParameters().isEmpty()) {
            jgen.writePOJOProperty("pathParameters", httpRequest.getPathParameters());
        }
        if (httpRequest.getQueryStringParameterList() != null && !httpRequest.getQueryStringParameterList().isEmpty()) {
            jgen.writePOJOProperty("queryStringParameters", httpRequest.getQueryStringParameters());
        }
        if (httpRequest.getHeaderList() != null && !httpRequest.getHeaderList().isEmpty()) {
            jgen.writePOJOProperty("headers", httpRequest.getHeaders());
        }
        if (httpRequest.getCookieList() != null && !httpRequest.getCookieList().isEmpty()) {
            jgen.writePOJOProperty("cookies", httpRequest.getCookies());
        }
        if (httpRequest.isKeepAlive() != null) {
            jgen.writeBooleanProperty("keepAlive", httpRequest.isKeepAlive());
        }
        if (httpRequest.isSecure() != null) {
            jgen.writeBooleanProperty("secure", httpRequest.isSecure());
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
        if (httpRequest.getBody() != null && isNotBlank(String.valueOf(httpRequest.getBody().getValue()))) {
            jgen.writePOJOProperty("body", httpRequest.getBody());
        }
        jgen.writeEndObject();
    }
}
