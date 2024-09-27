package com.pia.mockserver.util;

import java.util.*;
import org.mockserver.model.HttpRequest;

/** Utility class for extracting parameters from HTTP requests. */
public class HttpRequestUtil {

  private HttpRequestUtil() {}

  /**
   * Extracts the 'limit' parameter from the HTTP request. If the parameter is not found or is not a
   * valid integer, returns a default value of 10.
   *
   * @param httpRequest The HTTP request from which to extract the parameter.
   * @return The extracted limit value, constrained to a maximum of 10.
   */
  public static int extractLimit(HttpRequest httpRequest) {
    int limit = extractIntParameter(httpRequest, "limit", 10);
    return Math.min(limit, 10);
  }

  /**
   * Extracts the 'offset' parameter from the HTTP request. If the parameter is not found or is not
   * a valid integer, returns a default value of 0.
   *
   * @param httpRequest The HTTP request from which to extract the parameter.
   * @return The extracted offset value.
   */
  public static int extractOffset(HttpRequest httpRequest) {
    return extractIntParameter(httpRequest, "offset", 0);
  }

  /**
   * Extracts the 'sort' parameter from the HTTP request. Splits the parameter value by comma and
   * returns as a set of strings. If the parameter is not found, returns a set with a default value
   * of "createdDate".
   *
   * @param httpRequest The HTTP request from which to extract the parameter.
   * @return The extracted sort criteria as a set of strings.
   */
  public static Set<String> extractSort(HttpRequest httpRequest) {
    String sortParam = extractStringParameter(httpRequest, "sort", "createdDate");
    return new HashSet<>(Arrays.asList(sortParam.split(",")));
  }

  /**
   * Extracts the 'filter' parameter from the HTTP request. If the parameter is not found, returns
   * null.
   *
   * @param httpRequest The HTTP request from which to extract the parameter.
   * @return The extracted filter value, or null if not found.
   */
  public static String extractFilter(HttpRequest httpRequest) {
    return extractStringParameter(httpRequest, "filter", null);
  }

  /**
   * Extracts the 'fields' parameter from the HTTP request. Splits the parameter value by comma and
   * returns as a set of strings. If the parameter is not found or is empty, returns an empty set.
   *
   * @param httpRequest The HTTP request from which to extract the parameter.
   * @return The extracted fields as a set of strings, or an empty set if not found or empty.
   */
  public static Set<String> extractFields(HttpRequest httpRequest) {
    String fields = extractStringParameter(httpRequest, "fields", null);
    if (fields == null || fields.isEmpty()) {
      return Collections.emptySet();
    }
    return new HashSet<>(Arrays.asList(fields.split(",")));
  }

  private static int extractIntParameter(
      HttpRequest httpRequest, String parameterName, int defaultValue) {
    String parameterValue =
        httpRequest.getFirstQueryStringParameter(parameterName).isEmpty()
            ? null
            : httpRequest.getFirstQueryStringParameter(parameterName);
    return Optional.ofNullable(parameterValue)
        .filter(str -> str.matches("\\d+"))
        .map(Integer::parseInt)
        .orElse(defaultValue);
  }

  private static String extractStringParameter(
      HttpRequest httpRequest, String parameterName, String defaultValue) {
    String parameterValue =
        httpRequest.getFirstQueryStringParameter(parameterName).isEmpty()
            ? null
            : httpRequest.getFirstQueryStringParameter(parameterName);
    return Optional.ofNullable(parameterValue).orElse(defaultValue);
  }
}
