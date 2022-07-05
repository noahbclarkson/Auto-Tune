package unprotesting.com.github.data;

import java.util.HashMap;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import unprotesting.com.github.config.Config;
import unprotesting.com.github.data.Transaction.TransactionType;
import unprotesting.com.github.util.EconomyUtil;
import unprotesting.com.github.util.Format;

public class PurchaseUtil {

  /**
   * Purchase/sell an item from a shop.
   * @param name The name of the shop.
   * @param player The player uuid.
   * @param amount The item to purchase.
   */
  public static void purchaseItem(String name, Player player, int amount, boolean isBuy) {
    Shop shop = getAssociatedShop(player, name);

    if (shop == null) {
      return;
    }

    Component display = Shop.getDisplayName(name, shop.isEnchantment());
    double price = isBuy ? shop.getPrice() : shop.getSellPrice();
    double total = price * amount;
    double balance = EconomyUtil.getEconomy().getBalance(player);
    TagResolver r = getTagResolver(display, price, amount, balance, shop.getSetting());
    UUID uuid = player.getUniqueId();

    if (isBuy && !shop.isUnlocked(uuid)) {
      Format.sendMessage(player, Config.get().getNotUnlocked(), r);
      return;
    }

    if (isBuy && balance < total) {
      Format.sendMessage(player, Config.get().getNotEnoughMoney(), r);
      return;
    }

    if (Config.get().isEnableSellLimits()) {
      if (Database.get().getPurchasesLeft(name, uuid, isBuy) - amount < 0) {
        if (isBuy) {
          Format.sendMessage(player, Config.get().getRunOutOfBuys(), r);
        } else {
          Format.sendMessage(player, Config.get().getRunOutOfSells(), r);
        }
        return;
      }
    }

    boolean success = shop.isEnchantment() ? enchant(player, name, amount, isBuy, r)
        : item(player, name, amount, isBuy, r);

    if (!success) {
      return;
    }

    TransactionType position = isBuy ? TransactionType.BUY : TransactionType.SELL;
    Transaction transaction = new Transaction(price, amount, uuid, name, position);
    Database.get().transactions.put(System.currentTimeMillis(), transaction);
    EconomyDataUtil.increaseEconomyData("GDP", total / 2);

    if (isBuy) {
      shop.addBuys(uuid, amount);
    } else {
      shop.addSells(uuid, amount);
    }

    if (isBuy) {
      EconomyUtil.getEconomy().withdrawPlayer(player, total);
    } else {
      EconomyUtil.getEconomy().depositPlayer(player, total);
    }

    String message = isBuy ? Config.get().getShopPurchase() : Config.get().getShopSell();
    Format.sendMessage(player, message, r);
    double loss = shop.getPrice() * amount - total;
    EconomyDataUtil.increaseEconomyData("LOSS", loss);
    ShopUtil.putShop(name, shop);
  }

  /**
   * Sell an item stack to all relevant shops.
   * @param item The item stack to sell.
   * @param player The player object.
   */
  public static void sellItemStack(ItemStack item, Player player) {
    int amount = item.getAmount();
    boolean success = true;
    double total = 0;
    double balance = EconomyUtil.getEconomy().getBalance(player);
    UUID uuid = player.getUniqueId();
    TagResolver r = getTagResolver(item.displayName(), total / amount, amount, balance, null);

    for (Enchantment enchantment : item.getEnchantments().keySet()) {
      String name = enchantment.getKey().getKey();
      Shop shop = getAssociatedShop(player, name);

      if (shop == null) {
        Format.sendMessage(player, Config.get().getNotInShop(), r);
        success = false;
        break;
      }

      double price = shop.getSellPrice() * item.getEnchantmentLevel(enchantment);
      total += price * amount;
      r = getTagResolver(item.displayName(), price, amount, balance, null);

      if (Config.get().isEnableSellLimits()) {
        if (ShopUtil.getSellsLeft(player, name) - amount < 0) {
          Format.sendMessage(player, Config.get().getRunOutOfSells(), r);
          success = false;
          break;
        }
      }

    }

    String itemName = item.getType().toString().toLowerCase();
    Shop itemShop = ShopUtil.getShop(itemName);

    if (itemShop == null) {
      Format.sendMessage(player, Config.get().getNotInShop(), r);
      player.getInventory().addItem(item);
      return;
    }

    double price = itemShop.getSellPrice();
    total += price * amount;
    r = getTagResolver(item.displayName(), price, amount, balance, null);

    if (Config.get().isEnableSellLimits()) {
      if (ShopUtil.getSellsLeft(player, itemName) - amount < 0) {
        Format.sendMessage(player, Config.get().getRunOutOfSells(), r);
        success = false;
      }
    }

    r = getTagResolver(item.displayName(), total / amount, amount, balance, null);

    if (!success) {
      player.getInventory().addItem(item);
      return;
    }

    for (Enchantment enchantment : item.getEnchantments().keySet()) {

      String name = enchantment.getKey().getKey();
      Shop shop = getAssociatedShop(player, name);
      Transaction transaction = new Transaction(price, amount, uuid, name, TransactionType.SELL);
      Database.get().transactions.put(System.currentTimeMillis(), transaction);
      EconomyDataUtil.increaseEconomyData("GDP", total / 2);
      double loss = shop.getPrice() - shop.getSellPrice();
      EconomyDataUtil.increaseEconomyData("LOSS", loss * amount);
      shop.addSells(uuid, amount);
      ShopUtil.putShop(name, shop);
    }

    Transaction transaction = new Transaction(price, amount, uuid, itemName, TransactionType.SELL);
    Database.get().transactions.put(System.currentTimeMillis(), transaction);
    EconomyDataUtil.increaseEconomyData("GDP", total / 2);
    double loss = itemShop.getPrice() - itemShop.getSellPrice();
    EconomyDataUtil.increaseEconomyData("LOSS", loss * amount);
    itemShop.addSells(uuid, amount);
    ShopUtil.putShop(itemName, itemShop);
    EconomyUtil.getEconomy().depositPlayer(player, total);
    Format.sendMessage(player, Config.get().getShopSell(), r);

  }

  private static TagResolver getTagResolver(Component display, double price,
      int amount, double balance, CollectFirst cf) {
    TagResolver.Builder builder = TagResolver.builder();
    builder.resolver(TagResolver.resolver(
        Placeholder.component("item", display),
        Placeholder.parsed("total", Format.currency(price * amount)),
        Placeholder.parsed("price", Format.currency(price)),
        Placeholder.parsed("amount", Integer.toString(amount)),
        Placeholder.parsed("balance", Format.currency(balance))));
    if (cf != null) {
      builder.resolver(Placeholder.parsed("collect-first-setting",
          cf.getSetting().toString().toLowerCase()));
    }
    return builder.build();
  }

  private static Shop getAssociatedShop(Player player, String name) {
    name = name.toLowerCase();
    Shop shop = ShopUtil.getShop(name);

    if (shop == null) {
      Format.sendMessage(player, Config.get().getNotInShop());
      return null;
    }
    return shop;
  }

  private static boolean item(Player player, String name, int amount,
      boolean isBuy, TagResolver r) {
    PlayerInventory inv = player.getInventory();
    ItemStack item = new ItemStack(Material.matchMaterial(name), amount);
    HashMap<Integer, ItemStack> map = isBuy ? inv.addItem(item) : inv.removeItem(item);

    if (map.isEmpty()) {
      return true;
    }

    if (!isBuy) {
      ItemStack returned = map.get(0);
      returned.setAmount(amount - returned.getAmount());
      inv.addItem(returned);
      Format.sendMessage(player, Config.get().getNotEnoughItems(), r);
      return false;
    }

    Format.sendMessage(player, Config.get().getNotEnoughSpace(), r);
    player.getWorld().dropItemNaturally(player.getLocation(), map.get(0));
    return true;
  }

  private static boolean enchant(Player player, String name, int amount,
      boolean isBuy, TagResolver r) {
    Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(name));
    ItemStack item = player.getInventory().getItemInMainHand();

    if (item == null || item.getType() == Material.AIR) {
      Format.sendMessage(player, Config.get().getHoldItemInHand(), r);
      return false;
    }

    if (isBuy) {
      int level = item.getEnchantmentLevel(enchantment);

      try {
        item.addEnchantment(enchantment, level + amount);
      } catch (IllegalArgumentException e) {
        Format.sendMessage(player, Config.get().getEnchantmentError(), r);
        return false;
      }

      return true;
    } else {

      if (!item.containsEnchantment(enchantment)) {
        Format.sendMessage(player, Config.get().getHoldItemInHand(), r);
        return false;
      }

      if (item.getEnchantmentLevel(enchantment) < amount) {
        Format.sendMessage(player, Config.get().getNotEnoughItems(), r);
        return false;
      }

      if (item.getEnchantmentLevel(enchantment) == amount) {
        item.removeEnchantment(enchantment);
      } else {
        item.addEnchantment(enchantment, item.getEnchantmentLevel(enchantment) - amount);
      }

      return true;

    }
  }



}
