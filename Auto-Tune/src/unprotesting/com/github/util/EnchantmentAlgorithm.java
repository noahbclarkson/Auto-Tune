package unprotesting.com.github.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import unprotesting.com.github.Main;

public class EnchantmentAlgorithm {

    public static void loadEnchantmentSettings() {
        ConfigurationSection config = Main.getEnchantmentConfig().getConfigurationSection("enchantments");
        ConcurrentHashMap<String, EnchantmentSetting> newMap = new ConcurrentHashMap<String, EnchantmentSetting>();
        if (Main.enchMap.containsKey("Auto-Tune")) {
            newMap = Main.enchMap.get("Auto-Tune");
            for (String str : config.getKeys(false)) {
                if (!newMap.containsKey(str)) {
                    Main.debugLog("Loaded new enchantment: " + str + ".");
                    EnchantmentSetting setting = new EnchantmentSetting(str);
                    newMap.put(str, setting);
                }
            }
        } else {
            for (String str : config.getKeys(false)) {
                if (!newMap.containsKey(str)) {
                    Main.debugLog("Loaded new enchantment: " + str + ".");
                    EnchantmentSetting setting = new EnchantmentSetting(str);
                    newMap.put(str, setting);
                }
            }
        }
        Main.enchMap.put("Auto-Tune", newMap);
    }

    @Deprecated
    public static double calculatePriceWithEnch(ItemStack is, boolean buy) {
        ItemMeta iMeta = is.getItemMeta();
        Map<Enchantment, Integer> enchants = iMeta.getEnchants();
        ConcurrentHashMap<Integer, Double[]> inMap = Main.map.get(is.getType().toString());
        double price = 0.0;
        if (inMap != null) {
            price = inMap.get(inMap.size() - 1)[0];
        }
        double cachePrice = 0;
        for (Map.Entry<Enchantment, Integer> ench : enchants.entrySet()) {
            String enchName = ench.getKey().getName();
            EnchantmentSetting setting = Main.enchMap.get("Auto-Tune").get(enchName);
            Double enchPrice = setting.price;
            enchPrice = enchPrice * ench.getValue();
            Double ratio = setting.ratio;
            if (!buy){
                enchPrice = enchPrice - (enchPrice*0.01*Config.getEnchantmentLimiter());
            }
            cachePrice = cachePrice + (price * ratio) + enchPrice;
        }
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
    public static void updateEnchantSellData(ItemStack is){
        ItemMeta iMeta = is.getItemMeta();
        Map<Enchantment, Integer> enchants = iMeta.getEnchants();
        for (Map.Entry<Enchantment, Integer> ench : enchants.entrySet()) {
            String enchName = ench.getKey().getName();
            EnchantmentSetting setting = Main.enchMap.get("Auto-Tune").get(enchName);
            ConcurrentHashMap<Integer, Double[]> map = setting.buySellData;
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
            arr[2] += 1;
            map.put(size, arr);
            setting.buySellData = map;
            ConcurrentHashMap<String, EnchantmentSetting> map_2 = Main.enchMap.get("Auto-Tune");
            map_2.put(enchName, setting);
            Main.enchMap.put("Auto-Tune", map_2);
        } 
    }
}
