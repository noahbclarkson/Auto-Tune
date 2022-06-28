package unprotesting.com.github.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
public class CollectFirst implements Serializable {

  private static final long serialVersionUID = 4793403925265988249L;

  // The CollectFirst setting
  @Getter
  protected final CollectFirstSetting setting;
  // The list of players who have collected the item
  protected List<UUID> players;
  // Whether the item has been found anywhere on the server
  @Getter @Setter
  protected boolean foundInServer;
  
  /**
   * Constructor for the collect first class.
   * @param setting The collect first setting for this shop.
   */
  protected CollectFirst(String cfSetting) {

    cfSetting = cfSetting.toLowerCase();
    if (cfSetting.equalsIgnoreCase("player")) {
      this.setting = CollectFirstSetting.PLAYER;
    } else if (cfSetting.equalsIgnoreCase("server")) {
      this.setting = CollectFirstSetting.SERVER;
    } else {
      this.setting = CollectFirstSetting.NONE;
    }

    this.players = new ArrayList<>();
    this.foundInServer = false;
  }

  public static enum CollectFirstSetting {
    PLAYER,
    SERVER,
    NONE
  }

  /**
   * Adds a player to the list of players who have collected the item.
   */
  public void addPlayer(UUID player) {
    if (!players.contains(player)) {
      players.add(player);
    }
    
  }

  /**
   * Whether or not the player is in the map.
   * I.e they have found this item.
   * @param player The player uuid.
   * @return Whether or not the player is in the map.
   */
  public boolean playerFound(UUID player) {
    return players.contains(player);
  }

  
}
