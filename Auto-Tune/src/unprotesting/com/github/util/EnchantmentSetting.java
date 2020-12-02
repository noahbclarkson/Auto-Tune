package unprotesting.com.github.util;

import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;

import unprotesting.com.github.Main;

public class EnchantmentSetting implements Serializable{
    
    private static final long serialVersionUID = 2393067834138849688L;
    public ConcurrentHashMap<Integer, Double[]> buySellData;
    public String name;
    public double price;
    public double ratio;

    public EnchantmentSetting(String name){
        this.name = name;
        price = Main.getEnchantmentConfig().getDouble("enchantments." + name + ".price");
        ratio = Main.getEnchantmentConfig().getDouble("enchantments." + name + ".ratio");
        buySellData = new ConcurrentHashMap <Integer, Double[]>();
    }
}
