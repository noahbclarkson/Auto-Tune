package unprotesting.com.github.data.objects;

import java.io.IOException;
import java.io.Serializable;

import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import org.mapdb.Serializer;

public class EconomyData implements Serializable {

  private static final long serialVersionUID = -2148852211944505714L;

  private double[] values;

  /**
   * Constructor.
   */
  private EconomyData(double[] start) {

    if (start == null) {
      this.values = new double[1];
    }

    this.values = start;
  }

  public EconomyData() {
    this.values = new double[1];
  }

  /**
   * Update the last value.
   * @param newValue The new value.
   */
  public void update(double newValue) {
    this.values[this.values.length - 1] = newValue;
  }

  /**
   * Get the last value.
   * @return The last value.
   */
  public double getValue() {
    return this.values[this.values.length - 1];
  }

  /**
   * Increase the value of the last value.
   * @param increase The amount to increase the last value by.
   */
  public void increase(double increase) {
    this.values[this.values.length - 1] += increase;
  }

  /**
   * Add a new time period.
   */
  public void addTimePeriod(double newValue) {
    // Increase size of array by 1 and add a new value as the last element.
    double[] newValues = new double[values.length + 1];
    System.arraycopy(values, 0, newValues, 0, values.length);
    newValues[values.length] = newValue;
    this.values = newValues;
  }

  public static class EconomyDataSerializer implements Serializer<EconomyData> {

    @Override
    public void serialize(DataOutput2 out, EconomyData value) throws IOException {
      out.writeInt(value.values.length);
      for (double d : value.values) {
        out.writeDouble(d);
      }
    }

    @Override
    public EconomyData deserialize(DataInput2 input, int available) throws IOException {
      int size = input.readInt();
      double[] values = new double[size];
      for (int i = 0; i < size; i++) {
        values[i] = input.readDouble();
      }
      return new EconomyData(values);
    }

  }




  
}
