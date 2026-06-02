package com.noahblclarkson.autotune.util;

import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.manager.EconomyMetricsManager;
import com.noahblclarkson.autotune.manager.MarketEngine;
import com.noahblclarkson.autotune.manager.ShopManager;
import com.noahblclarkson.autotune.model.ShopItem;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Optional;

/**
 * PlaceholderAPI expansion for Auto-Tune.
 * <p>
 * Placeholders:
 * <ul>
 *   <li>{@code %autotune_price_<material>%} — current mid-price</li>
 *   <li>{@code %autotune_buy_<material>%} — buy price (mid + BPD)</li>
 *   <li>{@code %autotune_sell_<material>%} — sell price (mid − SPD)</li>
 *   <li>{@code %autotune_spread_<material>%} — total spread % (BPD + SPD)</li>
 *   <li>{@code %autotune_bpd_<material>%} — buy spread %</li>
 *   <li>{@code %autotune_spd_<material>%} — sell spread %</li>
 *   <li>{@code %autotune_trend_<material>%} — price trend arrow (▲/▼/—)</li>
 *   <li>{@code %autotune_gdp%} — economy GDP</li>
 *   <li>{@code %autotune_debt%} — total debt</li>
 *   <li>{@code %autotune_inflation%} — inflation label</li>
 *   <li>{@code %autotune_volume%} — global volume multiplier (e.g. 1.05x)</li>
 *   <li>{@code %autotune_items%} — number of tracked items</li>
 *   <li>{@code %autotune_frozen%} — market frozen status (true/false)</li>
 * </ul>
 */
public class AutoTunePlaceholders extends PlaceholderExpansion {

    private final AutoTune plugin;
    private final ShopManager shopManager;
    private final MarketEngine marketEngine;
    private final EconomyMetricsManager metricsManager;
    private final ConfigManager configManager;

    public AutoTunePlaceholders(
            @NotNull AutoTune plugin,
            @NotNull ShopManager shopManager,
            @NotNull MarketEngine marketEngine,
            @NotNull EconomyMetricsManager metricsManager,
            @NotNull ConfigManager configManager
    ) {
        this.plugin = plugin;
        this.shopManager = shopManager;
        this.marketEngine = marketEngine;
        this.metricsManager = metricsManager;
        this.configManager = configManager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "autotune";
    }

    @Override
    public @NotNull String getAuthor() {
        return "noahblclarkson";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        // Don't unregister on reload — we manage our own lifecycle
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        String lower = params.toLowerCase(Locale.ROOT);

        // ─── Global economy placeholders ──────────────────────────────────
        switch (lower) {
            case "gdp" -> {
                return metricsManager.getLatestSnapshot()
                        .map(s -> configManager.formatCurrency(s.gdp()))
                        .orElse("N/A");
            }
            case "debt" -> {
                return metricsManager.getLatestSnapshot()
                        .map(s -> configManager.formatCurrency(s.totalDebt()))
                        .orElse("N/A");
            }
            case "inflation" -> {
                return metricsManager.getInflationLabel();
            }
            case "volume" -> {
                return String.format("%.2fx", marketEngine.getGlobalVolumeMultiplier());
            }
            case "items" -> {
                return String.valueOf(shopManager.getAllItems().size());
            }
            case "frozen" -> {
                return String.valueOf(marketEngine.isFrozen());
            }
        }

        // ─── Per-item placeholders: <type>_<material> ─────────────────────
        int underscoreIdx = lower.indexOf('_');
        if (underscoreIdx <= 0 || underscoreIdx >= lower.length() - 1) {
            return null;
        }

        String type = lower.substring(0, underscoreIdx);
        String materialName = lower.substring(underscoreIdx + 1).toUpperCase(Locale.ROOT);

        Material mat = Material.matchMaterial(materialName);
        if (mat == null) {
            return null;
        }

        Optional<ShopItem> itemOpt = shopManager.getItemByMaterial(mat);
        if (itemOpt.isEmpty()) {
            return null;
        }

        ShopItem item = itemOpt.get();
        BigDecimal price = marketEngine.getCurrentPrice(item.id());
        MarketEngine.SpreadResult spread = marketEngine.getSpread(item.id());

        return switch (type) {
            case "price" -> configManager.formatCurrency(price);
            case "buy" -> {
                BigDecimal buyPrice = price.multiply(BigDecimal.ONE.add(spread.bpd()));
                yield configManager.formatCurrency(buyPrice.setScale(2, RoundingMode.HALF_UP));
            }
            case "sell" -> {
                BigDecimal sellPrice = price.multiply(BigDecimal.ONE.subtract(spread.spd()));
                yield configManager.formatCurrency(sellPrice.setScale(2, RoundingMode.HALF_UP));
            }
            case "spread" -> {
                double totalSpread = (spread.bpd().doubleValue() + spread.spd().doubleValue()) * 100;
                yield String.format("%.1f%%", totalSpread);
            }
            case "bpd" -> String.format("%.2f%%", spread.bpd().doubleValue() * 100);
            case "spd" -> String.format("%.2f%%", spread.spd().doubleValue() * 100);
            case "trend" -> {
                // Simple trend indicator from recent price history
                BigDecimal change = marketEngine.get24hChange(item.id());
                if (change.compareTo(BigDecimal.ZERO) > 0) {
                    yield "▲";
                } else if (change.compareTo(BigDecimal.ZERO) < 0) {
                    yield "▼";
                } else {
                    yield "—";
                }
            }
            default -> null;
        };
    }
}
