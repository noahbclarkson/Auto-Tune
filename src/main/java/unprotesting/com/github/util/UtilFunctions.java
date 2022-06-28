package unprotesting.com.github.util;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class UtilFunctions {

  /**
   * Calculates the number of players online.
   * @return The number of players online.
   */
  public static int calculatePlayerCount() {

    int output = 0;

    // Loop through all online players to see if they should be added to the player count.
    for (Player player : Bukkit.getServer().getOnlinePlayers()) {

      try {
        output += shouldAddOne(player);
      } catch (NoClassDefFoundError e) {
        output++;
        continue;
      }

    }
    return output;

  }

  /**
   * Calculates whether to add one to the player count.
   * @param player The player to check.
   * @return Value to add to the player count
   */
  public static int shouldAddOne(Player player) {
    Essentials ess = (Essentials) Bukkit.getServer().getPluginManager().getPlugin("Essentials");

    if (ess == null) {
      return 1;
    }

    User user = new User(player, ess);

    // If user if AFK or vanished and "ignore-afk" is enabled, don't add one to the player count.
    if (user.isAfk() || user.isVanished()) {
      return 0;
    }

    return 1;
  }
  
}
