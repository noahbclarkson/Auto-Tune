package unprotesting.com.github.util;

import com.earth2me.essentials.User;

import java.text.DecimalFormat;

import lombok.Getter;
import lombok.Setter;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import unprotesting.com.github.Main;
import unprotesting.com.github.config.Config;

public class UtilFunctions {

  @Getter
  @Setter
  private static DecimalFormat df;

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

    User user = new User(player, Main.getInstance().getEss());

    // If "ignore-afk" is disabled, add one to the player count.
    if (!Config.getConfig().isIgnoreAfk()) {
      return 1;
    }

    // If user if AFK or vanished and "ignore-afk" is enabled, don't add one to the player count.
    if (user.isAfk() || user.isVanished()) {
      return 0;
    }

    return 1;

  }

  /**
   * Calculates the new price of an item using the pricing formula.
   * 
   * <p>If buys > sells then:
   * 
   * <p><code>
   *  price = p + p * v1 * 0.01 * (b/(b+s)) + p * v2 * 0.01
   * </code>
   * 
   * <p>If sells > buys then:
   * 
   * <p><code>
   * price = p - p * v1 * 0.01 * (s/(b+s)) - p * v2 * 0.01
   * </code>
   * 
   * <p>Where:
   * 
   * <p><code>p</code> is the previous price.
   * 
   * <p><code>v1</code> is the max volatility.
   * 
   * <p><code>v2</code> is the min volatility.
   * 
   * <p><code>b</code> is the average buy value.
   * 
   * <p><code>s</code> is the average sell value.
   * 
   * @param p The previous price.
   * @param volatility The volatility.
   * @param b The average buy value.
   * @param s The average sell value.
   * @return The new price.
   */
  public static double calculateNewPrice(double p, double[] volatility, double b, double s) {

    double total = b + s;

    // Check if buys > sells and calculate the new price based on the formula.
    if (b > s) {
      return p + p * volatility[0] * 0.01 * (b / total) + p * 0.01 * volatility[1];
    } else if (b < s) {
      return p - p * volatility[0] * 0.01 * (s / total) - p * 0.01 * volatility[1];
    } else {
      return p;
    }

  }


}
