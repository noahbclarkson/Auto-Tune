package unprotesting.com.github.data.objects;

import java.io.IOException;
import java.io.Serializable;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;

import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import org.mapdb.Serializer;

@Data
@AllArgsConstructor
public class Transaction implements Serializable {

  private static final long serialVersionUID = -7234917640151336711L;

  private double price;
  private int amount;
  private UUID player;
  private String item;
  private SalePositionType position;
  private TransactionType type;

  public static enum TransactionType {

    ITEM,
    ENCHANTMENT

  }

  public static enum SalePositionType {

    BUY,
    SELL

  }

  public static class TransactionSerializer implements Serializer<Transaction> {

    @Override
    public void serialize(DataOutput2 out, Transaction value) throws IOException {
        
      out.writeDouble(value.getPrice());
      out.writeInt(value.getAmount());
      UUID.serialize(out, value.getPlayer());
      out.writeUTF(value.getItem());
      out.writeUTF(value.getPosition().name());
      out.writeUTF(value.getType().name());

    }

    @Override
    public Transaction deserialize(DataInput2 input, int available) throws IOException {
        
      double price = input.readDouble();
      int amount = input.readInt();
      UUID player = UUID.deserialize(input, available);
      String item = input.readUTF();
      SalePositionType position = SalePositionType.valueOf(input.readUTF());
      TransactionType type = TransactionType.valueOf(input.readUTF());
      
      return new Transaction(price, amount, player, item, position, type);

    }

  }

}
