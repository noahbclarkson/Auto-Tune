package unprotesting.com.github.data;

import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.data.Transaction.TransactionType;
import unprotesting.com.github.util.EconomyUtil;
import unprotesting.com.github.util.Format;

import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;

/**
 * A utility class for purchasing and selling items.
 */
@UtilityClass
public class PurchaseUtil {

    /**
     * Purchase/sell an item from a shop.
     *
     * @param name   The name of the shop.
     * @param player The player uuid.
     * @param amount The item to purchase.
     */
    public void purchaseItem(String name, Player player, int amount, boolean isBuy) {
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
        Config config = Config.get();

        if (isBuy && !shop.isUnlocked(uuid)) {
            Format.sendMessage(player, config.getNotUnlocked(), r);
            return;
        }

        if (isBuy && balance < total) {
            Format.sendMessage(player, config.getNotEnoughMoney(), r);
            return;
        }

        if (Config.get().isEnableSellLimits()
                && Database.get().getPurchasesLeft(name, uuid, isBuy) - amount < 0) {
            if (isBuy) {
                Format.sendMessage(player, config.getRunOutOfBuys(), r);
            } else {
                Format.sendMessage(player, config.getRunOutOfSells(), r);
            }
            return;
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

        String message = isBuy ? config.getShopPurchase() : config.getShopSell();
        Format.sendMessage(player, message, r);
        double loss = shop.getPrice() * amount - total;
        EconomyDataUtil.increaseEconomyData("LOSS", loss);
        ShopUtil.putShop(name, shop);
    }

    /**
     * Sell an item stack to all relevant shops.
     *
     * @param item   The item stack to sell.
     * @param player The player object.
     */
    public void sellItemStack(ItemStack item, Player player) {
        int amount = item.getAmount();
        boolean success = true;
        double total = 0;
        double balance = EconomyUtil.getEconomy().getBalance(player);
        UUID uuid = player.getUniqueId();
        TagResolver r = getTagResolver(item.displayName(), total / amount, amount, balance, null);
        Config config = Config.get();

        for (Enchantment enchantment : item.getEnchantments().keySet()) {
            String enchantmentName = enchantment.getKey().getKey();
            Shop shop = getAssociatedShop(player, enchantmentName);
            if (shop == null) {
                Format.sendMessage(player, config.getNotInShop(), r);
                success = false;
                break;
            }

            double price = shop.getSellPrice() * item.getEnchantmentLevel(enchantment);
            price = scalePriceToDurability(item, price);
            total += price * amount;
            r = getTagResolver(item.displayName(), price, amount, balance, null);

            if (config.isEnableSellLimits()
                    && ShopUtil.getSellsLeft(player, enchantmentName) - amount < 0) {
                Format.sendMessage(player, config.getRunOutOfSells(), r);
                success = false;
                break;
            }

        }

        String itemName = item.getType().toString().toLowerCase();
        Shop itemShop = ShopUtil.getShop(itemName, true);
        if (itemShop == null) {
            Format.sendMessage(player, config.getNotInShop(), r);
            returnItem(player, item);
            return;
        }

        double price = itemShop.getSellPrice();
        price = scalePriceToDurability(item, price);

        if (price == 0) {
            Format.sendMessage(player, config.getNotInShop(), r);
            returnItem(player, item);
            return;
        }

        total += price * amount;
        r = getTagResolver(item.displayName(), price, amount, balance, null);

        if (config.isEnableSellLimits() && ShopUtil.getSellsLeft(player, itemName) - amount < 0) {
            Format.sendMessage(player, config.getRunOutOfSells(), r);
            success = false;
        }

        r = getTagResolver(item.displayName(), total / amount, amount, balance, null);

        if (!success) {
            returnItem(player, item);
            return;
        }

        for (Enchantment enchantment : item.getEnchantments().keySet()) {
            String name = enchantment.getKey().getKey();
            Shop shop = getAssociatedShop(player, name);
            createTransaction(amount, total, uuid, name, shop, price);
        }

        createTransaction(amount, total, uuid, itemName, itemShop, price);
        EconomyUtil.getEconomy().depositPlayer(player, total);
        Format.sendMessage(player, config.getShopSell(), r);

    }

    private void returnItem(Player player, ItemStack item) {
        HashMap<Integer, ItemStack> failed = player.getInventory().addItem(item);
        if (!failed.isEmpty()) {
            player.getWorld().dropItem(player.getLocation(), failed.get(0));
        }
    }

    private void createTransaction(int amount, double total, UUID uuid, String itemName,
            Shop itemShop, double price) {
        Transaction transaction = new Transaction(
                price, amount, uuid, itemName, TransactionType.SELL);
        Database.get().transactions.put(System.currentTimeMillis(), transaction);
        EconomyDataUtil.increaseEconomyData("GDP", total / 2);
        double loss = itemShop.getPrice() - itemShop.getSellPrice();
        EconomyDataUtil.increaseEconomyData("LOSS", loss * amount);
        itemShop.addSells(uuid, amount);
        ShopUtil.putShop(itemName, itemShop);
    }

    public TagResolver getTagResolver(Component display, double price,
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

    private Shop getAssociatedShop(Player player, String itemName) {
        String name = itemName.toLowerCase();
        Shop shop = ShopUtil.getShop(name, true);
        if (shop == null) {
            Format.sendMessage(player, Config.get().getNotInShop());
            return null;
        }

        return shop;
    }

    private boolean item(Player player, String name, int amount,
            boolean isBuy, TagResolver r) {
        PlayerInventory inv = player.getInventory();
        ItemStack item = new ItemStack(Objects.requireNonNull(
                Material.matchMaterial(name)), amount);
        HashMap<Integer, ItemStack> map = isBuy ? inv.addItem(item) : inv.removeItem(item);

        if (scalePriceToDurability(item, 1) == 0 && !isBuy) {
            inv.addItem(item);
            Format.sendMessage(player, Config.get().getNotInShop(), r);
            return false;
        }

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
        player.getWorld().dropItem(player.getLocation(), map.get(0));
        return true;
    }

    private boolean enchant(Player player, String name, int amount,
            boolean isBuy, TagResolver r) {
        Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(name));
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.getType() == Material.AIR) {
            Format.sendMessage(player, Config.get().getHoldItemInHand(), r);
            return false;
        }

        if (isBuy) {
            int level = item.getEnchantmentLevel(enchantment);
            if (canEnchant(item, enchantment, amount)) {
                item.addEnchantment(enchantment, level + amount);
                return true;
            } else {
                Format.sendMessage(player, Config.get().getEnchantmentError(), r);
                return false;
            }
        } else {
            if (!item.containsEnchantment(enchantment)) {
                Format.sendMessage(player, Config.get().getHoldItemInHand(), r);
                return false;
            }

            if (item.getEnchantmentLevel(enchantment) < amount) {
                Format.sendMessage(player, Config.get().getNotEnoughItems(), r);
                return false;
            } else if (item.getEnchantmentLevel(enchantment) == amount) {
                item.removeEnchantment(enchantment);
            } else {
                try {
                    item.addEnchantment(enchantment,
                            item.getEnchantmentLevel(enchantment) - amount);
                } catch (IllegalArgumentException e) {
                    Format.sendMessage(player, Config.get().getEnchantmentError(), r);
                    return false;
                }
            }
            return true;
        }
    }

    private boolean canEnchant(ItemStack item, Enchantment enchantment, int addedLevels) {
        if (!enchantment.canEnchantItem(item)) {
            return false;
        } else if (item.containsEnchantment(enchantment)) {
            return item.getEnchantmentLevel(enchantment) + addedLevels <= enchantment.getMaxLevel();
        } 
        for (Enchantment enchantment2 : item.getEnchantments().keySet()) {
            if (enchantment2.conflictsWith(enchantment)) {
                return false;
            }
        }
        return true;
    }

    private double scalePriceToDurability(ItemStack item, double sellPrice) {
        if (item.getItemMeta() instanceof Damageable) {
            Damageable damageable = (Damageable) item.getItemMeta();
            double durability = damageable.getHealth();
            double maxDurability = item.getType().getMaxDurability();

            if (Config.get().isDurabilityFunction()) {
                return sellPrice * (maxDurability - durability) / maxDurability;
            }

            if (durability != maxDurability) {
                return 0;
            }

        }

        return sellPrice;
    }

}
