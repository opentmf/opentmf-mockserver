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

    long days = d.toDaysPart(); // 0-..   (24-hour chunks)
    int hours = d.toHoursPart(); // 0-23
    int minutes = d.toMinutesPart(); // 0-59
    int seconds = d.toSecondsPart(); // 0-59
    int millis = d.toMillisPart(); // 0-999

    List<String> pieces = new ArrayList<>();

    if (days > 0) pieces.add(days + (days == 1 ? " day" : " days"));
    if (hours > 0) pieces.add(hours + (hours == 1 ? " hour" : " hours"));
    if (minutes > 0) pieces.add(minutes + (minutes == 1 ? " minute" : " minutes"));
    if (seconds > 0 || pieces.isEmpty())
      pieces.add(seconds + (seconds == 1 ? " second" : " seconds"));
    if (millis > 0)
      pieces.add(millis + (millis == 1 ? " millisecond" : " milliseconds"));

    // Oxford-comma style:  “2 hours, 12 minutes and 56 seconds”
    if (pieces.size() == 1) {
      return pieces.get(0);
    } else {
      String last = pieces.remove(pieces.size() - 1);
      return String.join(", ", pieces) + " and " + last;
    }
  }
}
