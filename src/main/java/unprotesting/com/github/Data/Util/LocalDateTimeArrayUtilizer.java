package unprotesting.com.github.Data.Util;

import java.time.LocalDateTime;

//  Abstract class for time-specific data and time-period objects

public abstract class LocalDateTimeArrayUtilizer {

    //  Convert recently de-serialized LocalDateTime object to a LocalDateTime

    public LocalDateTime arrayToDate(int[] time){
        return LocalDateTime.of(time[5], time[4], time[3], time[2], time[1], time[0], 0);
    }

    //  Convert LocalDateTime to serializable integer array

    public int[] dateToIntArray(LocalDateTime date){
        int[] output = new int[6];
        output[0] = date.getSecond();
        output[1] = date.getMinute();
        output[2] = date.getHour();
        output[3] = date.getDayOfMonth();
        output[4] = date.getMonthValue();
        output[5] = date.getYear();
        return output;
    }
    
}
