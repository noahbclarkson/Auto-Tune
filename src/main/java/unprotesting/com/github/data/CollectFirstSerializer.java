package unprotesting.com.github.data;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import org.mapdb.Serializer;
import unprotesting.com.github.data.CollectFirst.CollectFirstSetting;

/**
 * Serializer for CollectFirst class.
 */
public class CollectFirstSerializer implements Serializer<CollectFirst> {

    @Override
    public void serialize(DataOutput2 out, CollectFirst value) throws IOException {
        out.writeUTF(value.setting.name());
        out.writeInt(value.players.size());

        for (UUID player : value.players) {
            UUID.serialize(out, player);
        }

        out.writeBoolean(value.foundInServer);
    }

    @Override
    public CollectFirst deserialize(DataInput2 input, int available) throws IOException {
        CollectFirstSetting setting = CollectFirstSetting.valueOf(input.readUTF());
        int size = input.readInt();
        List<UUID> players = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            players.add(UUID.deserialize(input, available));
        }

        boolean foundInServer = input.readBoolean();
        return new CollectFirst(setting, players, foundInServer);
    }
}
