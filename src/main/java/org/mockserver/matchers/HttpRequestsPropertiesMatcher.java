package org.mockserver.matchers;

import java.util.*;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.*;
import org.mockserver.serialization.ObjectMapperFactory;
import org.slf4j.event.Level;
import tools.jackson.databind.ObjectWriter;

public class HttpRequestsPropertiesMatcher extends AbstractHttpRequestMatcher {

    private static final ObjectWriter TO_STRING_OBJECT_WRITER = ObjectMapperFactory.createObjectMapper(true, false);
    private int hashCode;
    private OpenAPIDefinition openAPIDefinition;
    private List<HttpRequestPropertiesMatcher> httpRequestPropertiesMatchers;
    private List<HttpRequest> httpRequests;

    protected HttpRequestsPropertiesMatcher(Configuration configuration, MockServerLogger mockServerLogger) {
        super(configuration, mockServerLogger);
    }

    public List<HttpRequestPropertiesMatcher> getHttpRequestPropertiesMatchers() {
        return httpRequestPropertiesMatchers;
    }

    @Override
    public List<HttpRequest> getHttpRequests() {
        return httpRequests;
    }

    @Override
    boolean apply(RequestDefinition requestDefinition) {
        throw new UnsupportedOperationException("OpenAPI definitions are not supported in this build");
    }

    @Override
    public boolean matches(MatchDifference context, RequestDefinition requestDefinition) {
        boolean result = false;
        if (httpRequestPropertiesMatchers != null && !httpRequestPropertiesMatchers.isEmpty()) {
            for (HttpRequestPropertiesMatcher httpRequestPropertiesMatcher : httpRequestPropertiesMatchers) {
                if (context == null) {
                    if (MockServerLogger.isEnabled(Level.TRACE) && requestDefinition instanceof HttpRequest) {
                        context = new MatchDifference(configuration.detailedMatchFailures(), requestDefinition);
                    }
                    result = httpRequestPropertiesMatcher.matches(context, requestDefinition);
                } else {
                    MatchDifference singleMatchDifference = new MatchDifference(configuration.detailedMatchFailures(), context.getHttpRequest());
                    result = httpRequestPropertiesMatcher.matches(singleMatchDifference, requestDefinition);
                    context.addDifferences(singleMatchDifference.getAllDifferences());
                }
                if (result) {
                    break;
                }
            }
        } else {
            result = true;
        }
        return result;
    }

    @Override
    public String toString() {
        try {
            return TO_STRING_OBJECT_WRITER
                .writeValueAsString(openAPIDefinition);
        } catch (Exception e) {
            return super.toString();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (hashCode() != o.hashCode()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        HttpRequestsPropertiesMatcher that = (HttpRequestsPropertiesMatcher) o;
        return Objects.equals(openAPIDefinition, that.openAPIDefinition);
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Objects.hash(super.hashCode(), openAPIDefinition);
        }
        return hashCode;
    }
}
