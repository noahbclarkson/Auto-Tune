package unprotesting.com.github.util;

public class MathUtil {

  /**
   * Add up all the values in an integer array.
   * @param array The array to add up
   * @return The sum of all the values in the array
   */
  public static int sumIntArray(int[] array) {
    int sum = 0;
    for (int i : array) {
      sum += i;
    }
    return sum;
  }

  /**
   * Add up all the values in a double array.
   * @param array The array to add up
   * @return The sum of all the values in the array
   */
  public static double sumDoubleArray(double[] array) {
    double sum = 0;
    for (double i : array) {
      sum += i;
    }
    return sum;
  }
  
}
