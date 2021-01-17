package unprotesting.com.github.util;

import java.io.IOException;
import java.util.Date;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import org.mapdb.serializer.GroupSerializerObjectArray;

public class TransactionSerializer extends GroupSerializerObjectArray<Transaction> {

    @Override
    public void serialize(DataOutput2 out, Transaction value) throws IOException {
        out.writeUTF(value.date.toString());
        out.writeUTF(value.item);
        out.writeUTF(value.player);
        out.writeUTF(value.type);
        out.writeInt(value.amount);
        out.writeDouble(value.total_price);
    }

    @Override
    @Deprecated
    public Transaction deserialize(DataInput2 input, int available) throws IOException {
        Date date = new Date(Date.parse(input.readUTF()));
        String item = input.readUTF();
        Player player = Bukkit.getPlayer(input.readUTF());
        String type = input.readUTF();
        int amount = input.readInt();
        double total_price = input.readDouble();
        return new Transaction(date, player, item, amount, type, total_price);
    }
    
}
