package org.mockserver.serialization;

import java.util.Arrays;
import java.util.List;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.slf4j.event.Level;
import tools.jackson.core.util.DefaultIndenter;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.databind.ObjectWriter;

/**
 * @author jamesdbloom
 */
public class LogEntrySerializer {
  private final MockServerLogger mockServerLogger;
  private static final ObjectWriter objectWriter =
      ObjectMapperFactory.createObjectMapper()
          .writer()
          .with(
              new DefaultPrettyPrinter()
                  .withArrayIndenter(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE)
                  .withObjectIndenter(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE));

  public LogEntrySerializer(MockServerLogger mockServerLogger) {
    this.mockServerLogger = mockServerLogger;
  }

  public String serialize(LogEntry logEntry) {
    try {
      return objectWriter.writeValueAsString(logEntry);
    } catch (Exception e) {
      mockServerLogger.logEvent(
          new LogEntry()
              .setLogLevel(Level.ERROR)
              .setMessageFormat(
                  "exception while serializing LogEntry to JSON with value " + logEntry)
              .setThrowable(e));
      throw new RuntimeException(
          "Exception while serializing LogEntry to JSON with value " + logEntry, e);
    }
  }

  public String serialize(List<LogEntry> logEntries) {
    return serialize(logEntries.toArray(new LogEntry[0]));
  }

  public String serialize(LogEntry... logEntries) {
    try {
      if (logEntries != null && logEntries.length > 0) {
        return objectWriter.writeValueAsString(logEntries);
      } else {
        return "[]";
      }
    } catch (Exception e) {
      mockServerLogger.logEvent(
          new LogEntry()
              .setLogLevel(Level.ERROR)
              .setMessageFormat(
                  "exception while serializing LogEntry to JSON with value "
                      + Arrays.asList(logEntries))
              .setThrowable(e));
      throw new RuntimeException(
          "Exception while serializing LogEntry to JSON with value " + Arrays.asList(logEntries),
          e);
    }
  }
}
