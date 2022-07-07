package unprotesting.com.github.data;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import org.mapdb.Serializer;

/**
 * The serializer for the Shop class.
 */
public class ShopSerializer implements Serializer<Shop> {

  @Override
  public void serialize(DataOutput2 out, Shop value) throws IOException {
    out.writeInt(value.size);

    for (int i = 0; i < value.size; i++) {
      out.writeInt(value.buys[i]);
      out.writeInt(value.sells[i]);
      out.writeDouble(value.prices[i]);
    }

    out.writeBoolean(value.enchantment);
    new CollectFirstSerializer().serialize(out, value.setting);
    out.writeInt(value.autosell.size());

    for (Map.Entry<UUID, Integer> entry : value.autosell.entrySet()) {
      UUID.serialize(out, entry.getKey());
      out.writeInt(entry.getValue());
    }

    out.writeInt(value.totalBuys);
    out.writeInt(value.totalSells);
    out.writeBoolean(value.locked);

    if (value.customSpd == -1) {
      out.writeBoolean(false);
    } else {
      out.writeBoolean(true);
      out.writeDouble(value.customSpd);
    }

    out.writeDouble(value.volatility);
    out.writeDouble(value.change);
    out.writeInt(value.maxBuys);
    out.writeInt(value.maxSells);
    out.writeInt(value.updateRate);
    out.writeInt(value.timeSinceUpdate);
    out.writeUTF(value.section);
    out.writeInt(value.recentBuys.size());

    for (Map.Entry<UUID, Integer> entry : value.recentBuys.entrySet()) {
      UUID.serialize(out, entry.getKey());
      out.writeInt(entry.getValue());
    }

    out.writeInt(value.recentSells.size());

    for (Map.Entry<UUID, Integer> entry : value.recentSells.entrySet()) {
      UUID.serialize(out, entry.getKey());
      out.writeInt(entry.getValue());
    }

  }

  @Override
  public Shop deserialize(DataInput2 input, int available) throws IOException {
    Shop.ShopBuilder builder = new Shop.ShopBuilder();
    int size = input.readInt();
    builder.size(size);
    int[] buys = new int[size];
    int[] sells = new int[size];
    double[] prices = new double[size];
    for (int i = 0; i < size; i++) {
      buys[i] = input.readInt();
      sells[i] = input.readInt();
      prices[i] = input.readDouble();
    }
    builder.buys(buys);
    builder.sells(sells);
    builder.prices(prices);
    builder.enchantment(input.readBoolean());
    builder.setting(new CollectFirstSerializer().deserialize(input, available));
    int autosellSize = input.readInt();
    Map<UUID, Integer> autosell = new HashMap<>();
    for (int i = 0; i < autosellSize; i++) {
      autosell.put(UUID.deserialize(input, available), input.readInt());
    }
    builder.autosell(autosell);
    builder.totalBuys(input.readInt());
    builder.totalSells(input.readInt());
    builder.locked(input.readBoolean());
    builder.customSpd(input.readBoolean() ? input.readDouble() : -1);
    builder.volatility(input.readDouble());
    builder.change(input.readDouble());
    builder.maxBuys(input.readInt());
    builder.maxSells(input.readInt());
    builder.updateRate(input.readInt());
    builder.timeSinceUpdate(input.readInt());
    builder.section(input.readUTF());
    int recentBuysSize = input.readInt();
    Map<UUID, Integer> recentBuys = new HashMap<>();
    for (int i = 0; i < recentBuysSize; i++) {
      recentBuys.put(UUID.deserialize(input, available), input.readInt());
    }
    builder.recentBuys(recentBuys);
    int recentSellsSize = input.readInt();
    Map<UUID, Integer> recentSells = new HashMap<>();
    for (int i = 0; i < recentSellsSize; i++) {
      recentSells.put(UUID.deserialize(input, available), input.readInt());
    }
    builder.recentSells(recentSells);
    return builder.build();
  }
}
