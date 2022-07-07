package unprotesting.com.github.data;

import java.io.IOException;
import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import org.mapdb.Serializer;

/**
 * Serializer for loan class.
 */
public class LoanSerializer implements Serializer<Loan> {

  @Override
  public void serialize(DataOutput2 out, Loan value) throws IOException {
    out.writeDouble(value.getValue());
    out.writeDouble(value.getBase());
    UUID.serialize(out, value.player);
    out.writeBoolean(value.isPaid());
  }

  @Override
  public Loan deserialize(DataInput2 in, int available) throws IOException {
    Loan.LoanBuilder builder = new Loan.LoanBuilder();
    builder.value(in.readDouble());
    builder.base(in.readDouble());
    builder.player(UUID.deserialize(in, available));
    builder.paid(in.readBoolean());
    return builder.build();
  }
}
