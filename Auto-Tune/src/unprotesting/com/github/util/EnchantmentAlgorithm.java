package unprotesting.com.github.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import unprotesting.com.github.Main;
import unprotesting.com.github.Commands.AutoTuneGUIShopUserCommand;

public class EnchantmentAlgorithm {

    public static void loadEnchantmentSettings() {
        ConfigurationSection config = Main.getEnchantmentConfig().getConfigurationSection("enchantments");
        if (Main.enchMap == null){
            Main.enchMap = new ConcurrentHashMap<String, EnchantmentSetting>();
        }
        for (String str : config.getKeys(false)) {
            if (!Main.enchMap.containsKey(str)) {
                Main.debugLog("Loaded new enchantment: " + str + ".");
                EnchantmentSetting setting = new EnchantmentSetting(str);
                Main.enchMap.put(str, setting);
            }
        } 
    }

    @Deprecated
    public static Double calculatePriceWithEnch(ItemStack is, boolean buy) {
        ItemMeta iMeta = is.getItemMeta();
        Map<Enchantment, Integer> enchants = iMeta.getEnchants();
        double price = 0.0;
        int checks = 0;
        double ratio = 0;
        try{
            price = AutoTuneGUIShopUserCommand.getItemPrice(is.getType().toString(), !buy);
        }
        catch(NullPointerException e){
            return null;
        }
        double cachePrice = 0;
        for (Map.Entry<Enchantment, Integer> ench : enchants.entrySet()) {
            String enchName = ench.getKey().getName();
            EnchantmentSetting setting = Main.enchMap.get(enchName);
            if (setting == null){
                continue;
            }
            Double enchPrice;
            try{
                enchPrice = setting.price;
            }
            catch(NullPointerException e){
                continue;
            }
            enchPrice = enchPrice * ench.getValue();
            if (!buy){
                enchPrice = enchPrice - (enchPrice*0.01*Config.getSellPriceDifference());
                enchPrice = enchPrice - (enchPrice*0.01*Config.getEnchantmentLimiter());
            }
            cachePrice += enchPrice;
            ratio++;
            checks++;
        }
        double newRatio = ratio/checks;
        cachePrice = (price*newRatio) + cachePrice;
        double durability = DurabilityAlgorithm.calculateDurability(is);
        if (durability != 100.00){
            if (cachePrice == 0) {
                price = price*durability*0.01;
                price = price - price*0.01*Config.getDurabilityLimiter();
                return price;
            } else {
                cachePrice = cachePrice*durability*0.01;
                cachePrice = cachePrice - cachePrice*0.01*Config.getDurabilityLimiter();
                return cachePrice;
            }
        }
        if (cachePrice == 0) {
            return price;
        } else {
            return cachePrice;
        }
    }

    @Deprecated
    public static void updateEnchantSellData(ItemStack is, Player player){
        ItemMeta iMeta = is.getItemMeta();
        Map<Enchantment, Integer> enchants = iMeta.getEnchants();
        for (Map.Entry<Enchantment, Integer> ench : enchants.entrySet()) {
            String enchName = ench.getKey().getName();
            EnchantmentSetting setting = Main.enchMap.get(enchName);
            ConcurrentHashMap<Integer, Double[]> map;
            try{
                map = setting.buySellData;
            }
            catch(NullPointerException e){
                map = new ConcurrentHashMap<Integer, Double[]>();
                map.put(0, new Double[]{0.0, 0.0, 0.0});
            }
            Integer size = map.size()-1;
            Double[] arr = map.get(size);
            if (arr == null){
                arr = new Double[]{setting.price, 0.0, 0.0};
            }
            if (arr[2] == null){
                arr[2] = 0.0;
            }
            if (arr[1] == null){
                arr[1] = 0.0;
            }
            arr[2] += ench.getValue();
            map.put(size, arr);
            setting.buySellData = map;
            Main.enchMap.put(enchName, setting);
            Transaction transaction = new Transaction(player, ench.getKey(), "Sell");
            transaction.loadIntoMap();
        } 
    }
}
