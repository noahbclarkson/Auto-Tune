package unprotesting.com.github.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import unprotesting.com.github.Main;
import unprotesting.com.github.Commands.AutoTuneGUIShopUserCommand;

public class EnchantmentAlgorithm {

    public static void loadEnchantmentSettings() {
        ConfigurationSection config = Main.getEnchantmentConfig().getConfigurationSection("enchantments");
        ConcurrentHashMap<String, EnchantmentSetting> newMap = Main.enchMap.get("Auto-Tune");
        if (newMap == null){
            newMap = new ConcurrentHashMap<String, EnchantmentSetting>();
        }
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
        double price = 0.0;
        try{
            price = AutoTuneGUIShopUserCommand.getItemPrice(is.getType().toString(), !buy);
        }
        catch(NullPointerException e){
            price = 0.0;
        }
        double cachePrice = 0;
        for (Map.Entry<Enchantment, Integer> ench : enchants.entrySet()) {
            String enchName = ench.getKey().getName();
            EnchantmentSetting setting = Main.enchMap.get("Auto-Tune").get(enchName);
            if (setting == null){
                loadEnchantmentSettings();
                setting = Main.enchMap.get("Auto-Tune").get(enchName);
                if (setting == null){
                    return 0.0;
                }
            }
            Double enchPrice;
            try{
                enchPrice = setting.price;
            }
            catch(NullPointerException e){
                loadEnchantmentSettings();
                setting = Main.enchMap.get("Auto-Tune").get(enchName);
                if (setting == null){
                    return 0.0;
                }
                enchPrice = setting.price;
            }
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
            arr[2] += 1;
            map.put(size, arr);
            setting.buySellData = map;
            ConcurrentHashMap<String, EnchantmentSetting> map_2 = Main.enchMap.get("Auto-Tune");
            map_2.put(enchName, setting);
            Main.enchMap.put("Auto-Tune", map_2);
        } 
    }
}
