package org.opentmf.mockserver.util;

/**
 * Utility class for extracting parts of a path. This class provides methods to extract the last
 * part of a path, extract the domain without ID, and extract the domain with ID.
 *
 * @author Gokhan Demir
 */
public class PathExtractor {

  private PathExtractor() {}

  /**
   * Extracts the last part of the given path.
   *
   * @param path The path from which to extract the last part.
   * @return The last part of the path.
   */
  public static String extractLastPart(String path) {
    if (path.endsWith("/")) {
      path = path.substring(0, path.length() - 1);
    }
    int pos = path.lastIndexOf('/');
    return path.substring(pos + 1);
  }

  /**
   * Extracts the domain without the ID part from the given path.
   *
   * @param path The path from which to extract the domain without ID.
   * @return The domain without the ID part.
   * @throws IllegalArgumentException if the input path is null or empty.
   */
  public static String extractDomainWithoutId(String path) {
    if (path == null || path.isEmpty()) {
      throw new IllegalArgumentException("Input path cannot be null or empty.");
    }

    path = path.trim();
    // Remove trailing slash
    path = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    path = path.startsWith("/") ? path.substring(1) : path;
    return path;
  }

  /**
   * Extracts the domain with the ID part from the given path.
   *
   * @param inputPath The path from which to extract the domain with ID.
   * @return The domain with the ID part.
   */
  public static String extractDomainWithId(String inputPath) {
    inputPath = extractDomainWithoutId(inputPath);
    // Remove the last id segment
    int pos = inputPath.lastIndexOf('/');
    inputPath = inputPath.substring(0, pos);
    // Split the path and handle empty segments
    return inputPath;
  }
}
