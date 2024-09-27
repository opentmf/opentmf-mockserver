package com.pia.mockserver.model;

import static com.pia.mockserver.model.TmfConstants.*;

import java.util.Arrays;
import java.util.Locale;
import org.apache.commons.lang3.StringUtils;

/**
 * Enumeration representing various paths and their associated states within a Telecommunications
 * Management Framework (TMF). Each enum constant represents a specific path along with its
 * corresponding initial and final states. This enumeration provides functionality to resolve a TMF
 * state path from a given string representation of the path.
 */
public enum TmfStatePath {

  /**
   * Enum constant representing the order path. Associated variable: STATE Initial state:
   * ACKNOWLEDGED Final state: COMPLETED
   */
  ORDER("order", STATE, ACKNOWLEDGED, COMPLETED),

  /**
   * Enum constant representing the inventory path. Associated variable: STATUS Initial state:
   * CREATED Final state: ACTIVE
   */
  INVENTORY("inventory", STATUS, CREATED, ACTIVE),

  /**
   * Enum constant representing the catalog path. Associated variable: LIFE_CYCLE_STATUS Initial
   * state: IN_STUDY Final state: IN_DESIGN
   */
  CATALOG("catalog", LIFE_CYCLE_STATUS, IN_STUDY, IN_DESIGN),

  /**
   * Enum constant representing the category path. Associated variable: LIFE_CYCLE_STATUS Initial
   * state: IN_STUDY Final state: IN_DESIGN
   */
  CATEGORY("category", LIFE_CYCLE_STATUS, IN_STUDY, IN_DESIGN),

  /**
   * Enum constant representing the candidate path. Associated variable: LIFE_CYCLE_STATUS Initial
   * state: IN_STUDY Final state: IN_DESIGN
   */
  CANDIDATE("candidate", LIFE_CYCLE_STATUS, IN_STUDY, IN_DESIGN),

  /**
   * Default enum constant representing a default path. Associated variable: STATE Initial state:
   * ACKNOWLEDGED Final state: COMPLETED
   */
  DEFAULT("default", STATE, ACKNOWLEDGED, COMPLETED);

  private final String path;
  private final String variableName;
  private final String initialState;
  private final String finalState;

  /**
   * Constructs a TMF state path enum constant with the specified path, variable name, initial
   * state, and final state.
   *
   * @param path The path name.
   * @param variableName The variable name associated with the path.
   * @param initialState The initial state associated with the path.
   * @param finalState The final state associated with the path.
   */
  TmfStatePath(String path, String variableName, String initialState, String finalState) {
    this.path = path;
    this.variableName = variableName;
    this.initialState = initialState;
    this.finalState = finalState;
  }

  /**
   * Resolves a TMF state path enum constant from the given string representation of the path. If
   * the provided path is empty or null, returns the default enum constant.
   *
   * @param path The string representation of the path.
   * @return The corresponding TMF state path enum constant, or the default enum constant if the
   *     provided path is empty or null.
   */
  public static TmfStatePath resolveFromPath(String path) {
    if (StringUtils.isEmpty(path)) {
      return DEFAULT;
    }
    return Arrays.stream(values())
        .filter(
            tmfStatePath ->
                path.toUpperCase(Locale.UK).contains(tmfStatePath.getPath().toUpperCase(Locale.UK)))
        .findFirst()
        .orElse(DEFAULT);
  }

  /**
   * Retrieves the path name.
   *
   * @return The path name.
   */
  public String getPath() {
    return path;
  }

  /**
   * Retrieves the variable name associated with the path.
   *
   * @return The variable name associated with the path.
   */
  public String getVariableName() {
    return variableName;
  }

  /**
   * Retrieves the initial state associated with the path.
   *
   * @return The initial state associated with the path.
   */
  public String getInitialState() {
    return initialState;
  }

  /**
   * Retrieves the final state associated with the path.
   *
   * @return The final state associated with the path.
   */
  public String getFinalState() {
    return finalState;
  }
}
