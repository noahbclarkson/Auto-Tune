package unprotesting.com.github.data;

import java.io.IOException;
import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import org.mapdb.Serializer;
import unprotesting.com.github.data.Transaction.TransactionType;

/**
 * Serializer for the Transaction class.
 */
public class TransactionSerializer implements Serializer<Transaction> {

    @Override
    public void serialize(DataOutput2 out, Transaction value) throws IOException {
        out.writeDouble(value.getPrice());
        out.writeInt(value.getAmount());
        UUID.serialize(out, value.getPlayer());
        out.writeUTF(value.getItem());
        out.writeUTF(value.getPosition().toString());
    }

    @Override
    public Transaction deserialize(DataInput2 in, int available) throws IOException {
        Transaction.TransactionBuilder builder = new Transaction.TransactionBuilder();
        builder.price(in.readDouble());
        builder.amount(in.readInt());
        builder.player(UUID.deserialize(in, available));
        builder.item(in.readUTF());
        builder.position(TransactionType.valueOf(in.readUTF()));
        return builder.build();
    }
}
