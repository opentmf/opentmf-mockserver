package org.mockserver.serialization;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.*;
import org.mockserver.serialization.deserializers.body.BodyDTODeserializer;
import org.mockserver.serialization.deserializers.body.BodyWithContentTypeDTODeserializer;
import org.mockserver.serialization.deserializers.collections.CookiesDeserializer;
import org.mockserver.serialization.deserializers.collections.HeadersDeserializer;
import org.mockserver.serialization.deserializers.collections.ParametersDeserializer;
import org.mockserver.serialization.deserializers.condition.TimeToLiveDTODeserializer;
import org.mockserver.serialization.deserializers.condition.VerificationTimesDTODeserializer;
import org.mockserver.serialization.deserializers.expectation.OpenAPIExpectationDTODeserializer;
import org.mockserver.serialization.deserializers.request.RequestDefinitionDTODeserializer;
import org.mockserver.serialization.deserializers.string.NottableStringDeserializer;
import org.mockserver.serialization.serializers.body.*;
import org.mockserver.serialization.serializers.certificate.CertificateSerializer;
import org.mockserver.serialization.serializers.certificate.X509CertificateSerializer;
import org.mockserver.serialization.serializers.collections.CookiesSerializer;
import org.mockserver.serialization.serializers.collections.HeadersSerializer;
import org.mockserver.serialization.serializers.collections.ParametersSerializer;
import org.mockserver.serialization.serializers.condition.VerificationTimesDTOSerializer;
import org.mockserver.serialization.serializers.condition.VerificationTimesSerializer;
import org.mockserver.serialization.serializers.expectation.OpenAPIExpectationDTOSerializer;
import org.mockserver.serialization.serializers.expectation.OpenAPIExpectationSerializer;
import org.mockserver.serialization.serializers.matcher.HttpRequestPropertiesMatcherSerializer;
import org.mockserver.serialization.serializers.matcher.HttpRequestsPropertiesMatcherSerializer;
import org.mockserver.serialization.serializers.request.HttpRequestDTOSerializer;
import org.mockserver.serialization.serializers.request.OpenAPIDefinitionDTOSerializer;
import org.mockserver.serialization.serializers.request.OpenAPIDefinitionSerializer;
import org.mockserver.serialization.serializers.response.*;
import org.mockserver.serialization.serializers.response.HttpResponseSerializer;
import org.mockserver.serialization.serializers.string.NottableStringSerializer;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

/**
 * @author jamesdbloom
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class ObjectMapperFactory {

  private static ObjectMapper objectMapper =
      buildObjectMapperWithDeserializerAndSerializers(
          Collections.emptyList(), Collections.emptyList(), false);
  private static final ObjectWriter prettyPrintWriter =
      buildObjectMapperWithDeserializerAndSerializers(
              Collections.emptyList(), Collections.emptyList(), false)
          .writerWithDefaultPrettyPrinter();
  private static final ObjectWriter prettyPrintWriterThatSerialisesDefaultFields =
      buildObjectMapperWithDeserializerAndSerializers(
              Collections.emptyList(), Collections.emptyList(), true)
          .writerWithDefaultPrettyPrinter();
  private static final ObjectWriter writer =
      buildObjectMapperWithDeserializerAndSerializers(
              Collections.emptyList(), Collections.emptyList(), false)
          .writer();

  public static ObjectMapper createObjectMapper() {
    if (objectMapper == null) {
      objectMapper =
          buildObjectMapperWithDeserializerAndSerializers(
              Collections.emptyList(), Collections.emptyList(), false);
    }
    return objectMapper;
  }

  public static ObjectMapper createObjectMapper(ValueSerializer... additionValueSerializers) {
    if (additionValueSerializers == null || additionValueSerializers.length == 0) {
      if (objectMapper == null) {
        objectMapper =
            buildObjectMapperWithDeserializerAndSerializers(
                Collections.emptyList(), Collections.emptyList(), false);
      }
      return objectMapper;
    } else {
      return buildObjectMapperWithDeserializerAndSerializers(
          Collections.emptyList(), Arrays.asList(additionValueSerializers), false);
    }
  }

  public static ObjectMapper createObjectMapper(
      ValueDeserializer... replacementValueDeserializers) {
    if (replacementValueDeserializers == null || replacementValueDeserializers.length == 0) {
      if (objectMapper == null) {
        objectMapper =
            buildObjectMapperWithDeserializerAndSerializers(
                Collections.emptyList(), Collections.emptyList(), false);
      }
      return objectMapper;
    } else {
      return buildObjectMapperWithDeserializerAndSerializers(
          Arrays.asList(replacementValueDeserializers), Collections.emptyList(), false);
    }
  }

  public static ObjectWriter createObjectMapper(
      boolean pretty, boolean serialiseDefaultValues, ValueSerializer... additionValueSerializers) {
    if (additionValueSerializers == null || additionValueSerializers.length == 0) {
      if (pretty && serialiseDefaultValues) {
        return prettyPrintWriterThatSerialisesDefaultFields;
      } else if (pretty) {
        return prettyPrintWriter;
      } else {
        return writer;
      }
    } else {
      if (pretty) {
        return buildObjectMapperWithDeserializerAndSerializers(
                Collections.emptyList(),
                Arrays.asList(additionValueSerializers),
                serialiseDefaultValues)
            .writerWithDefaultPrettyPrinter();
      } else {
        return buildObjectMapperWithDeserializerAndSerializers(
                Collections.emptyList(),
                Arrays.asList(additionValueSerializers),
                serialiseDefaultValues)
            .writer();
      }
    }
  }

  public static ObjectMapper buildObjectMapperWithoutRemovingEmptyValues() {
    return JsonMapper.builder()
        // ignore failures
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
        .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
        .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
        .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_VALUES)
        .enable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
        .enable(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS)
        .disable(MapperFeature.REQUIRE_SETTERS_FOR_GETTERS)
        // relax parsing
        .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        .enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
        .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
        .enable(JsonReadFeature.ALLOW_YAML_COMMENTS)
        .enable(JsonReadFeature.ALLOW_UNQUOTED_PROPERTY_NAMES)
        .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
        .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
        .enable(JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER)
        .enable(JsonReadFeature.ALLOW_LEADING_ZEROS_FOR_NUMBERS)
        .enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS)
        .enable(JsonReadFeature.ALLOW_MISSING_VALUES)
        .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
        // use arrays
        .enable(DeserializationFeature.USE_JAVA_ARRAY_FOR_JSON_ARRAY)
        // consistent json output
        .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
        .build();
  }

  public static ObjectMapper buildObjectMapperWithOnlyConfigurationDefaults() {
    JsonMapper base = (JsonMapper) buildObjectMapperWithoutRemovingEmptyValues();
    return base.rebuild()
        .changeDefaultPropertyInclusion(v -> v.withValueInclusion(JsonInclude.Include.NON_EMPTY))
        .build();
  }

  private static ObjectMapper buildObjectMapperWithDeserializerAndSerializers(
      List<ValueDeserializer> replacementValueDeserializers,
      List<ValueSerializer> replacementValueSerializers,
      boolean serialiseDefaultValues) {
    JsonMapper base = (JsonMapper) buildObjectMapperWithOnlyConfigurationDefaults();
    SimpleModule module = new SimpleModule();
    addDeserializers(module, replacementValueDeserializers.toArray(new ValueDeserializer[0]));
    addSerializers(
        module,
        replacementValueSerializers.toArray(new ValueSerializer[0]),
        serialiseDefaultValues);
    return base.rebuild().addModule(module).build();
  }

  private static void addDeserializers(
      SimpleModule module, ValueDeserializer[] replacementValueDeserializers) {
    List<ValueDeserializer> jsonDeserializers =
        Arrays.asList(
            // expectation
            new OpenAPIExpectationDTODeserializer(),
            // request
            new RequestDefinitionDTODeserializer(),
            // times
            new TimeToLiveDTODeserializer(),
            // request body
            new BodyDTODeserializer(),
            new BodyWithContentTypeDTODeserializer(),
            // condition
            new VerificationTimesDTODeserializer(),
            // nottable string
            new NottableStringDeserializer(),
            // key and multivalue
            new HeadersDeserializer(),
            new ParametersDeserializer(),
            new CookiesDeserializer());
    Map<Class, ValueDeserializer> jsonDeserializersByType = new HashMap<>();
    for (ValueDeserializer jsonDeserializer : jsonDeserializers) {
      jsonDeserializersByType.put(jsonDeserializer.handledType(), jsonDeserializer);
    }
    // override any existing deserializers
    for (ValueDeserializer additionValueDeserializer : replacementValueDeserializers) {
      jsonDeserializersByType.put(
          additionValueDeserializer.handledType(), additionValueDeserializer);
    }
    for (Map.Entry<Class, ValueDeserializer> additionValueDeserializer :
        jsonDeserializersByType.entrySet()) {
      module.addDeserializer(
          additionValueDeserializer.getKey(), additionValueDeserializer.getValue());
    }
  }

  private static void addSerializers(
      SimpleModule module,
      ValueSerializer[] replacementValueSerializers,
      boolean serialiseDefaultValues) {
    List<ValueSerializer> jsonSerializers =
        Arrays.asList(
            // expectation
            new OpenAPIExpectationSerializer(),
            new OpenAPIExpectationDTOSerializer(),
            // times
            new TimesSerializer(),
            new TimesDTOSerializer(),
            new TimeToLiveSerializer(),
            new TimeToLiveDTOSerializer(),
            // request
            new org.mockserver.serialization.serializers.request.HttpRequestSerializer(),
            new HttpRequestDTOSerializer(),
            new OpenAPIDefinitionSerializer(),
            new OpenAPIDefinitionDTOSerializer(),
            // request body
            new BinaryBodySerializer(),
            new BinaryBodyDTOSerializer(),
            new JsonBodySerializer(serialiseDefaultValues),
            new JsonBodyDTOSerializer(serialiseDefaultValues),
            new JsonSchemaBodySerializer(),
            new JsonSchemaBodyDTOSerializer(),
            new JsonPathBodySerializer(),
            new JsonPathBodyDTOSerializer(),
            new ParameterBodySerializer(),
            new ParameterBodyDTOSerializer(),
            new RegexBodySerializer(),
            new RegexBodyDTOSerializer(),
            new StringBodySerializer(serialiseDefaultValues),
            new StringBodyDTOSerializer(serialiseDefaultValues),
            new LogEntryBodySerializer(),
            new LogEntryBodyDTOSerializer(),
            // condition
            new VerificationTimesDTOSerializer(),
            new VerificationTimesSerializer(),
            // nottable string
            new NottableStringSerializer(),
            // response
            new HttpResponseSerializer(),
            new HttpResponseDTOSerializer(),
            // key and multivalue
            new HeadersSerializer(),
            new ParametersSerializer(),
            new CookiesSerializer(),
            // certificates
            new X509CertificateSerializer(),
            new CertificateSerializer(),
            // log
            new org.mockserver.serialization.serializers.log.LogEntrySerializer(),
            // matcher
            new HttpRequestsPropertiesMatcherSerializer(),
            new HttpRequestPropertiesMatcherSerializer());
    Map<Class, ValueSerializer> jsonSerializersByType = new HashMap<>();
    for (ValueSerializer jsonSerializer : jsonSerializers) {
      jsonSerializersByType.put(jsonSerializer.handledType(), jsonSerializer);
    }
    // override any existing serializers
    for (ValueSerializer additionValueSerializer : replacementValueSerializers) {
      jsonSerializersByType.put(additionValueSerializer.handledType(), additionValueSerializer);
    }
    for (Map.Entry<Class, ValueSerializer> additionValueSerializer :
        jsonSerializersByType.entrySet()) {
      module.addSerializer(additionValueSerializer.getKey(), additionValueSerializer.getValue());
    }
  }
}
