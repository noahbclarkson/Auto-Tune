package unprotesting.com.github.util;

import java.util.Date;
import java.util.Calendar;

public class MathHandler {
    public static Date addMinutesToJavaUtilDate(Date date, int minutes) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.MINUTE, minutes);
        return calendar.getTime();
      }
}