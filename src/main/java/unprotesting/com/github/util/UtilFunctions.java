package unprotesting.com.github.util;


import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class UtilFunctions {

  /**
   * Calculates the number of players online.
   * @return The number of players online.
   */
  public static int calculatePlayerCount() {
    return Bukkit.getOnlinePlayers().size();
  }
  
}
