package com.noahblclarkson.autotune.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.manager.MarketEventService;
import com.noahblclarkson.autotune.model.MarketEvent;
import com.noahblclarkson.autotune.model.MarketEvent.EventType;
import com.noahblclarkson.autotune.model.MarketEvent.Status;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Singleton
@SuppressWarnings("PMD")
public class EventCommand {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(ZoneId.of("UTC"));

    private final MarketEventService eventService;

    @Inject
    public EventCommand(MarketEventService eventService) {
        this.eventService = eventService;
    }

    // ─── /at event ─────────────────────────────────────────────────────────────

    @org.incendo.cloud.annotations.Command("at event")
    @org.incendo.cloud.annotations.Permission("autotune.admin")
    public void eventHelp(CommandSender sender) {
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Market Events", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text(" — Dynamic economy events", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/at event list", NamedTextColor.YELLOW)
                .append(Component.text(" — List recent events", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/at event active", NamedTextColor.YELLOW)
                .append(Component.text(" — Show currently active events", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/at event trigger <type> <materials> <mult> <mins>", NamedTextColor.YELLOW)
                .append(Component.text(" — Start an event now", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/at event cancel <id>", NamedTextColor.YELLOW)
                .append(Component.text(" — Cancel an active event", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/at event templates", NamedTextColor.YELLOW)
                .append(Component.text(" — List available event templates", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/at event invoke <name>", NamedTextColor.YELLOW)
                .append(Component.text(" — Trigger a template immediately", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/at event schedule <type> <mats> <mult> <dur> <offset>", NamedTextColor.YELLOW)
                .append(Component.text(" — Schedule event to start in N minutes", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("Types: ", NamedTextColor.GRAY)
                .append(formatEventTypes()));
        sender.sendMessage(Component.empty());
    }

    // ─── /at event list ────────────────────────────────────────────────────────

    @org.incendo.cloud.annotations.Command("at event list")
    @org.incendo.cloud.annotations.Permission("autotune.admin")
    public void eventList(CommandSender sender) {
        List<MarketEvent> events = eventService.listEvents();
        if (events.isEmpty()) {
            sender.sendMessage(Component.text("No market events found.", NamedTextColor.GRAY));
            return;
        }

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Recent Market Events", NamedTextColor.GOLD, TextDecoration.BOLD));
        for (MarketEvent event : events) {
            sender.sendMessage(formatEventRow(event, false));
        }
        sender.sendMessage(Component.empty());
    }

    // ─── /at event active ──────────────────────────────────────────────────────

    @org.incendo.cloud.annotations.Command("at event active")
    @org.incendo.cloud.annotations.Permission("autotune.admin")
    public void eventActive(CommandSender sender) {
        List<MarketEvent> active = eventService.getActiveEvents();
        if (active.isEmpty()) {
            sender.sendMessage(Component.text("No active market events.", NamedTextColor.GREEN));
            return;
        }

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Active Market Events", NamedTextColor.GOLD, TextDecoration.BOLD));
        for (MarketEvent event : active) {
            sender.sendMessage(formatEventRow(event, true));

            // Show materials affected
            if (event.materials() != null && !event.materials().isEmpty()) {
                String mats = String.join(", ", event.materials());
                sender.sendMessage(Component.text("  Materials: ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(mats, NamedTextColor.AQUA)));
            }

            // Show time remaining
            Instant now = Instant.now();
            if (event.endsAt() != null) {
                Duration remaining = Duration.between(now, event.endsAt());
                if (remaining.isPositive()) {
                    sender.sendMessage(Component.text("  Ends in: ", NamedTextColor.DARK_GRAY)
                            .append(Component.text(remaining.toMinutes() + " min", NamedTextColor.YELLOW))
                            .append(Component.text(" (" + TIME_FMT.format(event.endsAt()) + " UTC)", NamedTextColor.GRAY)));
                } else {
                    sender.sendMessage(Component.text("  Status: ", NamedTextColor.DARK_GRAY)
                            .append(Component.text("EXPIRED (will be cleaned up)", NamedTextColor.RED)));
                }
            }
        }
        sender.sendMessage(Component.empty());
    }

    // ─── /at event trigger ─────────────────────────────────────────────────────

    @org.incendo.cloud.annotations.Command("at event trigger <type> <materials> <multiplier> <minutes>")
    @org.incendo.cloud.annotations.Permission("autotune.admin")
    public void eventTrigger(
            CommandSender sender,
            @org.incendo.cloud.annotations.Argument("type") String typeStr,
            @org.incendo.cloud.annotations.Argument("materials") String materialsStr,
            @org.incendo.cloud.annotations.Argument("multiplier") Double multiplier,
            @org.incendo.cloud.annotations.Argument("minutes") Integer minutes
    ) {
        // Parse event type
        EventType type;
        try {
            type = EventType.valueOf(typeStr.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("Unknown event type: " + typeStr, NamedTextColor.RED)
                    .append(Component.text(". Valid types: ", NamedTextColor.GRAY))
                    .append(formatEventTypes()));
            return;
        }

        if (multiplier <= 0 || multiplier > 10) {
            sender.sendMessage(Component.text("Multiplier must be between 0.1 and 10.", NamedTextColor.RED));
            return;
        }

        if (minutes <= 0 || minutes > 10080) {
            sender.sendMessage(Component.text("Duration must be 1 minute to 7 days (10080 min).", NamedTextColor.RED));
            return;
        }

        // Parse materials (comma-separated)
        List<String> materials = Arrays.stream(materialsStr.split(","))
                .map(String::trim)
                .map(String::toUpperCase)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());

        if (materials.isEmpty()) {
            sender.sendMessage(Component.text("At least one material is required.", NamedTextColor.RED));
            return;
        }

        // Build default messages if not provided
        String startMsg = buildStartMessage(type, materials, multiplier, minutes);
        String endMsg = buildEndMessage(type);

        String creatorName = (sender instanceof Player p) ? p.getName() : "console";
        MarketEvent event = eventService.triggerEvent(
                type.name().replace("_", " ") + " Event",
                type,
                materials,
                multiplier,
                Duration.ofMinutes(minutes),
                startMsg,
                endMsg,
                creatorName
        );

        sender.sendMessage(Component.text("Market event started: ", NamedTextColor.GREEN)
                .append(Component.text(event.name(), NamedTextColor.GOLD))
                .append(Component.text(" (" + multiplier + "x, " + minutes + " min)", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("Event ID: ", NamedTextColor.GRAY)
                .append(Component.text(event.id().toString(), NamedTextColor.YELLOW)));
    }

    // ─── /at event cancel <id> ────────────────────────────────────────────────

    @org.incendo.cloud.annotations.Command("at event cancel <id>")
    @org.incendo.cloud.annotations.Permission("autotune.admin")
    public void eventCancel(CommandSender sender, @org.incendo.cloud.annotations.Argument("id") String idStr) {
        UUID id;
        try {
            id = UUID.fromString(idStr.trim());
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("Invalid event ID: " + idStr, NamedTextColor.RED));
            sender.sendMessage(Component.text("Use /at event list to see event IDs.", NamedTextColor.GRAY));
            return;
        }

        boolean cancelled = eventService.cancelEvent(id);
        if (cancelled) {
            sender.sendMessage(Component.text("Event cancelled.", NamedTextColor.YELLOW));
        } else {
            sender.sendMessage(Component.text("Event not found or already ended.", NamedTextColor.RED));
        }
    }

    // ─── /at event info <id> ──────────────────────────────────────────────────

    @org.incendo.cloud.annotations.Command("at event info <id>")
    @org.incendo.cloud.annotations.Permission("autotune.admin")
    public void eventInfo(CommandSender sender, @org.incendo.cloud.annotations.Argument("id") String idStr) {
        UUID id;
        try {
            id = UUID.fromString(idStr.trim());
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("Invalid event ID: " + idStr, NamedTextColor.RED));
            return;
        }

        List<MarketEvent> all = eventService.listEvents();
        MarketEvent event = all.stream().filter(e -> e.id().equals(id)).findFirst().orElse(null);

        if (event == null) {
            sender.sendMessage(Component.text("Event not found: " + id, NamedTextColor.RED));
            return;
        }

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text(event.name()).color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        sender.sendMessage(Component.text("Type: ", NamedTextColor.GRAY)
                .append(Component.text(event.type().name(), NamedTextColor.AQUA)));
        sender.sendMessage(Component.text("Multiplier: ", NamedTextColor.GRAY)
                .append(Component.text(event.priceMultiplier() + "x", NamedTextColor.YELLOW)));
        sender.sendMessage(Component.text("Status: ", NamedTextColor.GRAY)
                .append(Component.text(event.status().name(), colorForStatus(event.status()))));
        sender.sendMessage(Component.text("Starts: ", NamedTextColor.GRAY)
                .append(Component.text(TIME_FMT.format(event.startsAt()) + " UTC", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Ends: ", NamedTextColor.GRAY)
                .append(Component.text(TIME_FMT.format(event.endsAt()) + " UTC", NamedTextColor.WHITE)));
        if (event.materials() != null && !event.materials().isEmpty()) {
            sender.sendMessage(Component.text("Materials: ", NamedTextColor.GRAY)
                    .append(Component.text(String.join(", ", event.materials()), NamedTextColor.AQUA)));
        }
        sender.sendMessage(Component.text("Created by: ", NamedTextColor.GRAY)
                .append(Component.text(event.createdBy(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("ID: ", NamedTextColor.GRAY)
                .append(Component.text(event.id().toString(), NamedTextColor.DARK_GRAY)));
        sender.sendMessage(Component.empty());
    }

    // ─── /at event templates ──────────────────────────────────────────────────

    @org.incendo.cloud.annotations.Command("at event templates")
    @org.incendo.cloud.annotations.Permission("autotune.admin")
    public void eventTemplates(CommandSender sender) {
        List<com.noahblclarkson.autotune.config.AutoTuneConfig.MarketEventConfigEntry> templates = eventService.getTemplates();
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Event Templates", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text(" (from config — ", NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(templates.size()), NamedTextColor.AQUA))
                .append(Component.text(" available)", NamedTextColor.GRAY)));

        if (templates.isEmpty()) {
            sender.sendMessage(Component.text("No templates configured. Add events to market-events.events in config.yml.", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("Use /at event trigger to create events without a template.", NamedTextColor.DARK_GRAY));
        } else {
            sender.sendMessage(Component.text("Use /at event invoke <name> to trigger a template immediately.", NamedTextColor.DARK_GRAY));
            sender.sendMessage(Component.text("Use /at event schedule <...> <offset-mins> to schedule for later.", NamedTextColor.DARK_GRAY));
            sender.sendMessage(Component.empty());
            for (com.noahblclarkson.autotune.config.AutoTuneConfig.MarketEventConfigEntry t : templates) {
                Component row = Component.text("  ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(t.name(), NamedTextColor.YELLOW))
                        .append(Component.text(" (" + t.type().toLowerCase(Locale.ROOT) + ") ", NamedTextColor.GRAY))
                        .append(Component.text(t.multiplier() + "x ", NamedTextColor.AQUA))
                        .append(Component.text("· " + t.durationMinutes() + " min ", NamedTextColor.DARK_GRAY))
                        .append(Component.text("[" + String.join(", ", t.materials()) + "]", NamedTextColor.GREEN));
                sender.sendMessage(row);
            }
        }
        sender.sendMessage(Component.empty());
    }

    // ─── /at event invoke <template-name> ──────────────────────────────────────

    @org.incendo.cloud.annotations.Command("at event invoke <name>")
    @org.incendo.cloud.annotations.Permission("autotune.admin")
    public void eventInvoke(CommandSender sender, @org.incendo.cloud.annotations.Argument("name") String name) {
        Optional<MarketEvent> result = eventService.invokeTemplate(name.trim());
        if (result.isPresent()) {
            MarketEvent event = result.get();
            sender.sendMessage(Component.text("Template invoked: ", NamedTextColor.GREEN)
                    .append(Component.text(event.name(), NamedTextColor.GOLD)));
        } else {
            sender.sendMessage(Component.text("Template not found: '" + name + "'", NamedTextColor.RED));
            sender.sendMessage(Component.text("Use /at event templates to see available templates.", NamedTextColor.GRAY));
        }
    }

    // ─── /at event schedule ───────────────────────────────────────────────────

    @org.incendo.cloud.annotations.Command("at event schedule <type> <materials> <multiplier> <duration> <start-minutes>")
    @org.incendo.cloud.annotations.Permission("autotune.admin")
    public void eventSchedule(
            CommandSender sender,
            @org.incendo.cloud.annotations.Argument("type") String typeStr,
            @org.incendo.cloud.annotations.Argument("materials") String materialsStr,
            @org.incendo.cloud.annotations.Argument("multiplier") Double multiplier,
            @org.incendo.cloud.annotations.Argument("duration") Integer duration,
            @org.incendo.cloud.annotations.Argument("start-minutes") Integer startMinutes
    ) {
        // Parse event type
        EventType type;
        try {
            type = EventType.valueOf(typeStr.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("Unknown event type: " + typeStr, NamedTextColor.RED)
                    .append(Component.text(". Valid types: ", NamedTextColor.GRAY))
                    .append(formatEventTypes()));
            return;
        }

        if (multiplier <= 0 || multiplier > 10) {
            sender.sendMessage(Component.text("Multiplier must be between 0.1 and 10.", NamedTextColor.RED));
            return;
        }

        if (duration <= 0 || duration > 10080) {
            sender.sendMessage(Component.text("Duration must be 1 minute to 7 days (10080 min).", NamedTextColor.RED));
            return;
        }

        if (startMinutes <= 0 || startMinutes > 43200) {
            sender.sendMessage(Component.text("Start offset must be 1 minute to 30 days (43200 min).", NamedTextColor.RED));
            return;
        }

        // Parse materials
        List<String> materials = Arrays.stream(materialsStr.split(","))
                .map(String::trim)
                .map(String::toUpperCase)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());

        if (materials.isEmpty()) {
            sender.sendMessage(Component.text("At least one material is required.", NamedTextColor.RED));
            return;
        }

        Instant startsAt = Instant.now().plus(startMinutes, ChronoUnit.MINUTES);
        String name = type.name().replace("_", " ") + " Event";
        String startMsg = buildStartMessage(type, materials, multiplier, duration);
        String endMsg = buildEndMessage(type);
        String creatorName = (sender instanceof Player p) ? p.getName() : "console";

        MarketEvent event = eventService.scheduleEvent(
                name, type, materials, multiplier,
                Duration.ofMinutes(duration),
                startsAt, startMsg, endMsg, creatorName
        );

        sender.sendMessage(Component.text("Event scheduled: ", NamedTextColor.YELLOW)
                .append(Component.text(event.name(), NamedTextColor.GOLD)));
        sender.sendMessage(Component.text("Starts at: ", NamedTextColor.GRAY)
                .append(Component.text(TIME_FMT.format(startsAt) + " UTC", NamedTextColor.WHITE))
                .append(Component.text(" (" + startMinutes + " min from now)", NamedTextColor.DARK_GRAY)));
        sender.sendMessage(Component.text("Event ID: ", NamedTextColor.GRAY)
                .append(Component.text(event.id().toString(), NamedTextColor.YELLOW)));
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private Component formatEventTypes() {
        return Component.text(
                Arrays.stream(EventType.values())
                        .map(t -> t.name().toLowerCase(Locale.ROOT).replace("_", " "))
                        .collect(Collectors.joining(", ")),
                NamedTextColor.AQUA
        );
    }

    private Component formatEventRow(MarketEvent event, boolean active) {
        NamedTextColor statusColor = colorForStatus(event.status());

        Component idComponent = Component.text(event.id().toString().substring(0, 8) + "...", NamedTextColor.DARK_GRAY)
                .hoverEvent(HoverEvent.showText(Component.text("Click to copy full ID", NamedTextColor.GRAY)))
                .clickEvent(ClickEvent.copyToClipboard(event.id().toString()));

        return Component.text("[", NamedTextColor.DARK_GRAY)
                .append(Component.text(statusLabel(event.status()), statusColor))
                .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                .append(Component.text(event.name(), NamedTextColor.WHITE))
                .append(Component.text(" (" + event.type().name().toLowerCase(Locale.ROOT).replace("_", " ") + ")", NamedTextColor.GRAY))
                .append(Component.text(" " + event.priceMultiplier() + "x", NamedTextColor.YELLOW))
                .append(Component.text("  id:", NamedTextColor.DARK_GRAY))
                .append(idComponent);
    }

    private NamedTextColor colorForStatus(Status status) {
        return switch (status) {
            case ACTIVE -> NamedTextColor.GREEN;
            case SCHEDULED -> NamedTextColor.YELLOW;
            case ENDED -> NamedTextColor.GRAY;
            case CANCELLED -> NamedTextColor.RED;
        };
    }

    private String statusLabel(Status status) {
        return status.name().substring(0, 1);
    }

    private String buildStartMessage(EventType type, List<String> materials, double mult, int mins) {
        String matList = String.join(", ", materials);
        return switch (type) {
            case DEMAND_SURGE -> "<gold> Demand Surge! </gold> " + matList + " buy prices are " + mult + "x boosted for " + mins + " min!";
            case SUPPLY_GLUT -> "<gold> Supply Glut! </gold> " + matList + " sell prices are " + mult + "x boosted for " + mins + " min!";
            case INFLATION_BOOST -> "<gold> Inflation Boost! </gold> All prices rising " + mult + "x faster for " + mins + " min!";
            case DEFLATION_DROP -> "<gold> Deflation Drop! </gold> All prices falling " + mult + "x faster for " + mins + " min!";
            case GOLD_RUSH -> "<gold> Gold Rush! </gold> " + matList + " prices surging " + mult + "x for " + mins + " min!";
            case CUSTOM -> "<gold> Market Event! </gold> Prices shifting " + mult + "x for " + mins + " min!";
        };
    }

    private String buildEndMessage(EventType type) {
        return "<gray> Market event has ended. Prices returning to normal. </gray>";
    }
}
