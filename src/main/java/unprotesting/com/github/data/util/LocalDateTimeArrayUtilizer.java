package unprotesting.com.github.data.util;

import java.time.LocalDateTime;

public abstract class LocalDateTimeArrayUtilizer {

  /**
   * Converts a de-serialized LocalDateTime object to a LocalDateTime.
   * @param time The de-serialized LocalDateTime object.
   * @return The LocalDateTime.
   */
  public LocalDateTime arrayToDate(int[] time) {
    return LocalDateTime.of(time[5], time[4], time[3], time[2], time[1], time[0], 0);
  }

  /**
   * Converts a LocalDateTime to a serializable integer array.
   * @param date The LocalDateTime.
   * @return The serializable integer array.
   */
  public int[] dateToIntArray(LocalDateTime date) {

    return new int[]{

      date.getSecond(),
      date.getMinute(),
      date.getHour(),
      date.getDayOfMonth(),
      date.getMonthValue(),
      date.getYear()

    };

  }

}
