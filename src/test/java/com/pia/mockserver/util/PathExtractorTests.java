package com.pia.mockserver.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.UUID;
import java.util.stream.Stream;

class PathExtractorTests {

  @Test
  void extractLastPart_withValidPath_returnsLastPartSuccessfully() {
    Assertions.assertEquals("123", PathExtractor.extractLastPart("/serviceOrder/123"));
    Assertions.assertEquals("123", PathExtractor.extractLastPart("/serviceOrder/123/"));
  }

  @Test
  void extractLastPart_withEmptyPath_returnsEmptyString() {
    Assertions.assertEquals("", PathExtractor.extractLastPart(""));
  }

  @ParameterizedTest
  @MethodSource("providePathsForTestWithoutId")
  void extractDomain_withOutIdAndValidPath_returnsDomainSuccessfully(String inputPath, String expectedDomain) {
    Assertions.assertEquals(expectedDomain, PathExtractor.extractDomainWithoutId(inputPath));
  }

  private static Stream<Arguments> providePathsForTestWithoutId() {
    return Stream.of(
            Arguments.of("/serviceOrder/", "serviceOrder"),
            Arguments.of("serviceOrder/", "serviceOrder"),
            Arguments.of("/serviceOrder", "serviceOrder"),
            Arguments.of("serviceOrder", "serviceOrder"),
            Arguments.of("/serviceOrder/test/", "serviceOrder/test"),
            Arguments.of("/serviceOrder/test", "serviceOrder/test"),
            Arguments.of("serviceOrder/test/", "serviceOrder/test"),
            Arguments.of("serviceOrder/test", "serviceOrder/test"),
            Arguments.of("/tmf-api/resourceOrdering/v4/resourceOrder/", "tmf-api/resourceOrdering/v4/resourceOrder"),
            Arguments.of("/tmf-api/resourceOrdering/v4/resourceOrder", "tmf-api/resourceOrdering/v4/resourceOrder"),
            Arguments.of("tmf-api/resourceOrdering/v4/resourceOrder/", "tmf-api/resourceOrdering/v4/resourceOrder"),
            Arguments.of("tmf-api/resourceOrdering/v4/resourceOrder", "tmf-api/resourceOrdering/v4/resourceOrder")
    );
  }


  @ParameterizedTest
  @MethodSource("providePathsForTestWithId")
  void extractDomain_withIdAndValidPath_returnsDomainSuccessfully(String inputPath, String expectedDomain) {
    Assertions.assertEquals(expectedDomain, PathExtractor.extractDomainWithId(inputPath));
  }

  private static Stream<Arguments> providePathsForTestWithId() {
    return Stream.of(
            Arguments.of("/serviceOrder/123/", "serviceOrder"),
            Arguments.of("/serviceOrder/123", "serviceOrder"),
            Arguments.of("serviceOrder/123/", "serviceOrder"),
            Arguments.of("serviceOrder/123", "serviceOrder"),
            Arguments.of("/serviceOrder/test/", "serviceOrder"),
            Arguments.of("/serviceOrder/test", "serviceOrder"),
            Arguments.of("serviceOrder/test/", "serviceOrder"),
            Arguments.of("serviceOrder/test", "serviceOrder"),
            Arguments.of("/tmf-api/resourceOrdering/v4/resourceOrder/" + UUID.randomUUID() + "/", "tmf-api/resourceOrdering/v4/resourceOrder"),
            Arguments.of("tmf-api/resourceOrdering/v4/resourceOrder/" + UUID.randomUUID() + "/", "tmf-api/resourceOrdering/v4/resourceOrder"),
            Arguments.of("/tmf-api/resourceOrdering/v4/resourceOrder/" + UUID.randomUUID(), "tmf-api/resourceOrdering/v4/resourceOrder"),
            Arguments.of("tmf-api/resourceOrdering/v4/resourceOrder/" + UUID.randomUUID(), "tmf-api/resourceOrdering/v4/resourceOrder")
    );
  }
}
