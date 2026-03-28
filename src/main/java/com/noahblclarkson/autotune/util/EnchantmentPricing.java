package com.noahblclarkson.autotune.util;

import com.noahblclarkson.autotune.config.AutoTuneConfig.EnchantmentConfig;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Calculates price multipliers for enchanted items.
 * Enchanted items are worth more than their base material counterpart
 * because they provide gameplay advantages.
 *
 * Multipliers are cumulative — a DIAMOND_SWORD with Sharpness II and Fire Aspect
 * gets both multipliers applied (e.g. ×1.60 × 1.20).
 */
public final class EnchantmentPricing {

    private static final int MIN_ROMAN = 0;
    private static final int MAX_ROMAN = 10;

    private EnchantmentPricing() {
    }

    /**
     * Returns the total enchantment multiplier for an item.
     * Returns 1.0 if no applicable enchantments are found.
     * Returns 1.0 if enchantment pricing is disabled.
     */
    public static double getMultiplier(@NotNull ItemStack item, @NotNull EnchantmentConfig config) {
        if (!config.enabled()) {
            return 1.0;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasEnchants()) {
            return 1.0;
        }

        Map<Enchantment, Integer> enchantments = meta.getEnchants();
        if (enchantments.isEmpty()) {
            return 1.0;
        }

        double totalMultiplier = 1.0;
        for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            Enchantment enchant = entry.getKey();
            int level = entry.getValue();

            String enchantName = enchant.getKey().getKey().toUpperCase(Locale.ROOT);
            // Convert bukkit kebab-case names (if any) to standard CAPS format
            enchantName = enchantName.replace("-", "_");

            Double multiplier = getMultiplierForEnchant(enchantName, level, config);
            if (multiplier != null) {
                totalMultiplier *= multiplier;
            }
        }

        return totalMultiplier;
    }

    /**
     * Returns the multiplier for a specific enchantment at a given level,
     * or null if no multiplier is defined.
     */
    @Nullable
    private static Double getMultiplierForEnchant(
            @NotNull String enchantName,
            int level,
            @NotNull EnchantmentConfig config
    ) {
        Map<String, List<Double>> multipliers = config.enchantmentMultipliers();
        List<Double> levelMultipliers = multipliers.get(enchantName);
        if (levelMultipliers == null || levelMultipliers.isEmpty()) {
            return null;
        }

        // Levels are 1-indexed; list is 0-indexed
        int index = level - 1;
        if (index >= levelMultipliers.size()) {
            // Use highest available level multiplier for overpowered items
            index = levelMultipliers.size() - 1;
        }

        return levelMultipliers.get(index);
    }

    /**
     * Applies the enchantment multiplier to a base price.
     * Result is rounded to 2 decimal places.
     */
    @NotNull
    public static BigDecimal applyMultiplier(@NotNull BigDecimal basePrice,
                                             double multiplier) {
        return basePrice.multiply(BigDecimal.valueOf(multiplier))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Returns a human-readable label for the enchantments on an item.
     * E.g. "Sharpness II, Unbreaking I" or null if none.
     */
    @Nullable
    public static String getEnchantmentLabel(@NotNull ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasEnchants()) {
            return null;
        }

        Map<Enchantment, Integer> enchantments = meta.getEnchants();
        if (enchantments.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            String name = formatEnchantName(entry.getKey().getKey().getKey());
            sb.append(name).append(" ").append(toRoman(entry.getValue()));
        }

        return sb.toString();
    }

    /**
     * Formats a Minecraft namespaced key to a readable enchantment name.
     * E.g. "sharpness" → "Sharpness", "soul_speed" → "Soul Speed"
     */
    @NotNull
    private static String formatEnchantName(@NotNull String key) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c == '_' || c == '-') {
                result.append(' ');
            } else if (i == 0 || (i > 0 && (key.charAt(i - 1) == '_' || key.charAt(i - 1) == '-'))) {
                result.append(Character.toUpperCase(c));
            } else {
                result.append(Character.toLowerCase(c));
            }
        }
        // Capitalize first letter
        if (result.length() > 0) {
            result.setCharAt(0, Character.toUpperCase(result.charAt(0)));
        }
        return result.toString();
    }

    /**
     * Converts an integer to a Roman numeral (1-10 range).
     */
    @NotNull
    private static String toRoman(int n) {
        if (n <= MIN_ROMAN) return String.valueOf(n);
        if (n > MAX_ROMAN) return String.valueOf(n);
        String[] tens = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return tens[n];
    }
}
