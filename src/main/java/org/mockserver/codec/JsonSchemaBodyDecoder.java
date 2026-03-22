package org.mockserver.codec;

import static java.lang.Double.parseDouble;
import static java.lang.Long.parseLong;
import static java.util.jar.Attributes.Name.CONTENT_TYPE;
import static java.util.stream.Collectors.toList;
import static org.mockserver.model.NottableString.serialiseNottableString;

import java.util.*;
import javax.annotation.Nullable;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.BodyMatcher;
import org.mockserver.matchers.JsonSchemaMatcher;
import org.mockserver.mock.Expectation;
import org.mockserver.model.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.*;

public class JsonSchemaBodyDecoder {

  private static final String APPLICATION_X_WWW_FORM_URLENCODED =
      "application/x-www-form-urlencoded";
  private final Configuration configuration;
  private final MockServerLogger mockServerLogger;
  private final Expectation expectation;
  private final HttpRequest httpRequest;
  private final ExpandedParameterDecoder formParameterParser;

  public JsonSchemaBodyDecoder(
      Configuration configuration,
      MockServerLogger mockServerLogger,
      Expectation expectation,
      HttpRequest httpRequest) {
    this.configuration = configuration;
    this.mockServerLogger = mockServerLogger;
    this.expectation = expectation;
    this.httpRequest = httpRequest;
    formParameterParser = new ExpandedParameterDecoder(configuration, mockServerLogger);
  }

  public String convertToJson(HttpRequest request, BodyMatcher<?> bodyMatcher) {
    String bodyAsJson = request.getBodyAsString();
    String contentType = request.getFirstHeader(CONTENT_TYPE.toString());
    if (contentType.contains(APPLICATION_X_WWW_FORM_URLENCODED)) {
      ObjectNode objectNode = new ObjectNode(JsonNodeFactory.instance);
      Parameters parameters =
          formParameterParser.retrieveFormParameters(request.getBodyAsString(), false);
      if (bodyMatcher instanceof JsonSchemaMatcher) {
        splitParameters(((JsonSchemaMatcher) bodyMatcher).getParameterStyle(), parameters);
      }
      parameters
          .getEntries()
          .forEach(
              parameter ->
                  objectNode.set(
                      serialiseNottableString(parameter.getName()),
                      toJsonObject(
                          NottableString.serialiseNottableStrings(parameter.getValues()))));
      bodyAsJson = objectNode.toPrettyString();
    }
    return bodyAsJson;
  }

  private void splitParameters(
      Map<String, ParameterStyle> parameterStyles, Parameters bodyParameters) {
    if (parameterStyles != null && bodyParameters != null) {
      for (Map.Entry<String, ParameterStyle> parameterStyleEntry : parameterStyles.entrySet()) {
        for (Parameter bodyParameterEntry : bodyParameters.getEntries()) {
          if (parameterStyleEntry.getKey().equals(bodyParameterEntry.getName().getValue())) {
            bodyParameterEntry.replaceValues(
                new ExpandedParameterDecoder(configuration, mockServerLogger)
                    .splitOnDelimiter(
                        parameterStyleEntry.getValue(),
                        parameterStyleEntry.getKey(),
                        bodyParameterEntry.getValues()));
            bodyParameters.replaceEntry(bodyParameterEntry);
          }
        }
      }
    }
  }

  private static JsonNode toJsonObject(final Collection<String> values) {
    if (values.size() == 0) {
      return NullNode.getInstance();
    }
    if (values.size() == 1) {
      return toJsonObject(values.iterator().next());
    }
    return new ArrayNode(
        JsonNodeFactory.instance,
        values.stream().map(JsonSchemaBodyDecoder::toJsonObject).collect(toList()));
  }

  private static JsonNode toJsonObject(@Nullable final String value) {
    if (value == null || value.equalsIgnoreCase("null")) {
      return NullNode.getInstance();
    }
    final String trimmed = value.trim();
    if (trimmed.equalsIgnoreCase("false")) {
      return BooleanNode.getFalse();
    }
    if (trimmed.equalsIgnoreCase("true")) {
      return BooleanNode.getTrue();
    }
    try {
      return new LongNode(parseLong(trimmed));
    } catch (final NumberFormatException ignore) {
    }
    try {
      return new DoubleNode(parseDouble(trimmed));
    } catch (final NumberFormatException ignore) {
    }
    return new StringNode(trimmed);
  }
}
