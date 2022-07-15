package unprotesting.com.github.util;

import lombok.experimental.UtilityClass;

/**
 * The utility class for math operations.
 */
@UtilityClass
public class MathUtil {

    /**
     * Add up all the values in an integer array.
     *
     * @param array The array to add up
     * @return The sum of all the values in the array
     */
    public int sumIntArray(int[] array) {
        int sum = 0;
        for (int i : array) {
            sum += i;
        }
        return sum;
    }

    /**
     * Add up all the values in a double array.
     *
     * @param array The array to add up
     * @return The sum of all the values in the array
     */
    public double sumDoubleArray(double[] array) {
        double sum = 0;
        for (double i : array) {
            sum += i;
        }
        return sum;
    }

}
