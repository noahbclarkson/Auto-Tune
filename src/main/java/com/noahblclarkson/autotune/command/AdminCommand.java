package com.noahblclarkson.autotune.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.manager.ShopManager;
import com.noahblclarkson.autotune.model.ShopItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

@Singleton
public class AdminCommand {

    private final AutoTune plugin;
    private final ConfigManager configManager;
    private final ShopManager shopManager;

    @Inject
    public AdminCommand(
            AutoTune plugin,
            ConfigManager configManager,
            ShopManager shopManager
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.shopManager = shopManager;
    }

    @Command("autotune admin")
    @Permission("autotune.admin")
    public void adminInfo(CommandSender sender) {
        sender.sendMessage(Component.text("=== Auto-Tune Admin ===", NamedTextColor.GOLD));

    }

    @Suggestions("material-suggestion")
    public List<String> suggestMaterials(CommandContext<?> ctx, String input) {
        return shopManager.getAllItems().stream()
                .map(it -> it.material().name().toLowerCase(Locale.ROOT))
                .filter(name -> name.contains(input.toLowerCase(Locale.ROOT)))
                .limit(20)
                .toList();
    }

    private org.bukkit.Material parseMaterial(@NotNull String name) {
        org.bukkit.Material mat = org.bukkit.Material.matchMaterial(name.toUpperCase(Locale.ROOT));
        if (mat == null) {
            mat = org.bukkit.Material.matchMaterial(name);
        }
        return mat;
    }

    private String formatItem(ShopItem item) {
        return item.getDisplayNameOrMaterial();
    }
}
