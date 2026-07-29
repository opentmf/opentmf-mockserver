package org.opentmf.mockserver.util;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Gokhan Demir
 */
public class DurationUtil {

  private DurationUtil() {}

  public static String formatDuration(long milliseconds) {
    if (milliseconds < 0) {
      throw new IllegalArgumentException("Negative durations are not supported");
    }
    Duration d = Duration.ofMillis(milliseconds);
    List<String> pieces = new ArrayList<>();

    addPiece(pieces, d.toDaysPart(), "day", "days");
    addPiece(pieces, d.toHoursPart(), "hour", "hours");
    addPiece(pieces, d.toMinutesPart(), "minute", "minutes");
    int seconds = d.toSecondsPart();
    if (seconds > 0 || pieces.isEmpty()) {
      addPiece(pieces, seconds, "second", "seconds");
    }
    addPiece(pieces, d.toMillisPart(), "millisecond", "milliseconds");

    return joinOxford(pieces);
  }

  private static void addPiece(List<String> pieces, long value, String singular, String plural) {
    if (value > 0) {
      pieces.add(value + " " + (value == 1 ? singular : plural));
    }
  }

  /** Oxford-comma style: {@code "2 hours, 12 minutes and 56 seconds"}. */
  private static String joinOxford(List<String> pieces) {
    if (pieces.size() == 1) {
      return pieces.get(0);
    }
    String last = pieces.remove(pieces.size() - 1);
    return String.join(", ", pieces) + " and " + last;
  }
}
