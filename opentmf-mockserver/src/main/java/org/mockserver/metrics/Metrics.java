package org.mockserver.metrics;

import org.mockserver.configuration.Configuration;
import org.mockserver.model.Action;

/**
 * @author jamesdbloom
 */
public class Metrics {

  public Metrics(Configuration configuration) {}

  public static void clear() {}

  public static void clear(Name name) {}

  public void set(Name name, Integer value) {}

  public static Integer get(Name name) {
    return 0;
  }

  public void increment(Name name) {}

  public void increment(Action.Type type) {}

  public void decrement(Name name) {}

  public void decrement(Action.Type type) {}

  public static void clearRequestAndExpectationMetrics() {}

  public static void clearActionMetrics() {}

  public static void clearWebSocketMetrics() {}

  public enum Name {
    REQUESTS_RECEIVED_COUNT("Expectation not matched count"),
    EXPECTATIONS_NOT_MATCHED_COUNT("Expectation not matched count"),
    RESPONSE_EXPECTATIONS_MATCHED_COUNT("Response expectation matched count"),
    FORWARD_EXPECTATIONS_MATCHED_COUNT("Forward expectation matched count"),
    FORWARD_ACTIONS_COUNT("Action forward count"),
    FORWARD_TEMPLATE_ACTIONS_COUNT("Action forward template count"),
    FORWARD_CLASS_CALLBACK_ACTIONS_COUNT("Action forward class callback count"),
    FORWARD_OBJECT_CALLBACK_ACTIONS_COUNT("Action forward object callback count"),
    FORWARD_REPLACE_ACTIONS_COUNT("Action forward replace count"),
    RESPONSE_ACTIONS_COUNT("Action response count"),
    RESPONSE_TEMPLATE_ACTIONS_COUNT("Action response template count"),
    RESPONSE_CLASS_CALLBACK_ACTIONS_COUNT("Action response class callback count"),
    RESPONSE_OBJECT_CALLBACK_ACTIONS_COUNT("Action response object callback count"),
    ERROR_ACTIONS_COUNT("Action error count"),
    WEBSOCKET_CALLBACK_CLIENTS_COUNT("Websocket callback client count"),
    WEBSOCKET_CALLBACK_RESPONSE_HANDLERS_COUNT("Websocket callback response handler count"),
    WEBSOCKET_CALLBACK_FORWARD_HANDLERS_COUNT("Websocket callback forward handler count");

    public final String description;

    Name(String description) {
      this.description = description;
    }
  }
}
