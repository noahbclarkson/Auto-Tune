package unprotesting.com.github.util;

import org.bukkit.inventory.ItemStack;

public class DurabilityAlgorithm {
    
    @Deprecated
    public static double calculateDurability(ItemStack is){
        Double durability = (double) is.getDurability();
        double maxDurability = (double) is.getType().getMaxDurability();
        if (durability == 0 ){
            return 100.00;
        }
        double current = maxDurability - durability;
        double output = (current/maxDurability)*100;
        return output;
    }

}