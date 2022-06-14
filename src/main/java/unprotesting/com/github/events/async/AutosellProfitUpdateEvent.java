package unprotesting.com.github.events.async;

import java.text.DecimalFormat;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import lombok.Getter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import unprotesting.com.github.Main;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.config.Messages;
import unprotesting.com.github.data.ephemeral.data.AutosellData;
import unprotesting.com.github.economy.EconomyFunctions;

public class AutosellProfitUpdateEvent extends Event {

  @Getter
  private final HandlerList handlers = new HandlerList();

  /**
   * Updates the autosell profit.
   * @param isAsync Whether the event is being run async or not.
   */
  public AutosellProfitUpdateEvent(boolean isAsync) {

    super(isAsync);
    depositCachedMoney();
    Main.getInstance().setAutosellData(new AutosellData());

  }

  /**
   * Deposits the cached money.
   */
  private void depositCachedMoney() {

    DecimalFormat df = new DecimalFormat(Config.getConfig().getNumberFormat());
    ConcurrentHashMap<String, Double> data = Main.getInstance().getAutosellData().getData();

    /**
     * Loop through the cached money and deposit it for each player.
     */
    for (String uuid : data.keySet()) {

      OfflinePlayer offPlayer = Bukkit.getOfflinePlayer(UUID.fromString(uuid));

      if (data.get(uuid) == null || data.get(uuid) == 0) {
        continue;
      }

      EconomyFunctions.getEconomy().depositPlayer(offPlayer, data.get(uuid));

      // If the player is offline then skip the event.
      if (!offPlayer.isOnline()) {
        continue;
      }

      String amount = df.format(data.get(uuid));
      Player player = offPlayer.getPlayer();
      TagResolver resolver = TagResolver.resolver(Placeholder.parsed("total", amount));

      player.sendMessage(Main.getInstance().getMm().deserialize(
          Messages.getMessages().getAutoSellProfitUpdate(), resolver));

    }
    
  }

}