package com.noahblclarkson.autotune.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.config.AutoTuneConfig.WebConfig;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.database.EconomySnapshotRepository;
import com.noahblclarkson.autotune.database.ItemRepository;
import com.noahblclarkson.autotune.database.LoanRepository;
import com.noahblclarkson.autotune.database.PlayerRepository;
import com.noahblclarkson.autotune.database.TransactionRepository;
import com.noahblclarkson.autotune.economy.EconomyManager;
import com.noahblclarkson.autotune.economy.LoanManager;
import com.noahblclarkson.autotune.manager.EconomyMetricsManager;
import com.noahblclarkson.autotune.manager.MarketEngine;
import com.noahblclarkson.autotune.manager.MarketEventService;
import com.noahblclarkson.autotune.manager.ShopManager;
import com.noahblclarkson.autotune.model.MarketEvent;
import com.noahblclarkson.autotune.model.EconomySnapshot;
import com.noahblclarkson.autotune.model.Loan;
import com.noahblclarkson.autotune.model.PlayerData;
import com.noahblclarkson.autotune.model.PriceHistory;
import com.noahblclarkson.autotune.model.ShopItem;
import com.noahblclarkson.autotune.model.PortfolioDto;
import com.noahblclarkson.autotune.model.Transaction;
import com.noahblclarkson.autotune.service.PortfolioService;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.json.JsonMapper;
import io.javalin.websocket.WsContext;
import org.bukkit.Server;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.logging.Level;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Singleton
public class WebServer {

    // API response field names
    private static final String KEY_LIMIT = "limit";
    private static final String KEY_TIMESTAMP = "timestamp";

    private final AutoTune plugin;
    private final ConfigManager configManager;
    private final ItemRepository itemRepository;
    private final MarketEngine marketEngine;
    private final EconomySnapshotRepository snapshotRepository;
    private final EconomyMetricsManager economyMetricsManager;
    private final TransactionRepository transactionRepository;
    private final LoanRepository loanRepository;
    private final PlayerRepository playerRepository;
    private final LoanManager loanManager;
    private final ShopManager shopManager;
    private final EconomyManager economyManager;
    private final MarketEventService marketEventService;
    private final Server server;
    private final PortfolioService portfolioService;
    private final Gson gson;

    private Javalin app;
    private final Set<WsContext> wsClients = ConcurrentHashMap.newKeySet();

    @Inject
    public WebServer(
            AutoTune plugin,
            ConfigManager configManager,
            ItemRepository itemRepository,
            MarketEngine marketEngine,
            EconomySnapshotRepository snapshotRepository,
            EconomyMetricsManager economyMetricsManager,
            TransactionRepository transactionRepository,
            LoanRepository loanRepository,
            PlayerRepository playerRepository,
            LoanManager loanManager,
            ShopManager shopManager,
            EconomyManager economyManager,
            MarketEventService marketEventService,
            Server server
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.itemRepository = itemRepository;
        this.marketEngine = marketEngine;
        this.snapshotRepository = snapshotRepository;
        this.economyMetricsManager = economyMetricsManager;
        this.transactionRepository = transactionRepository;
        this.loanRepository = loanRepository;
        this.playerRepository = playerRepository;
        this.loanManager = loanManager;
        this.shopManager = shopManager;
        this.economyManager = economyManager;
        this.marketEventService = marketEventService;
        this.server = server;
        this.portfolioService = new PortfolioService(
                playerRepository, itemRepository, loanRepository, economyManager, server);
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
    }

    public void start() {
        WebConfig config = configManager.getConfig().web();

        app = Javalin.create(javalinConfig -> {
            javalinConfig.showJavalinBanner = false;

            javalinConfig.jsonMapper(new JsonMapper() {
                @NotNull
                @Override
                @SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"})
                public <T> T fromJsonString(@NotNull String json, @NotNull Type targetType) {
                    return gson.fromJson(json, targetType);
                }

                @NotNull
                @Override
                public String toJsonString(@NotNull Object obj, @NotNull Type type) {
                    return gson.toJson(obj, type);
                }
            });

            javalinConfig.staticFiles.add(staticFileConfig -> {
                staticFileConfig.hostedPath = "/";
                staticFileConfig.directory = "/web";
                staticFileConfig.location = Location.CLASSPATH;
            });
        });

        app.before(ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            ctx.header("Access-Control-Allow-Methods", "GET, OPTIONS");
            ctx.header("Access-Control-Allow-Headers", "Content-Type");
        });

        registerRoutes();

        if (config.websocketEnabled()) {
            registerWebSocket();
        }

        app.start(config.host(), config.port());
        plugin.getLogger().info("Web server started on " + config.host() + ":" + config.port());
    }

    public void stop() {
        if (app != null) {
            wsClients.clear();
            app.stop();
            plugin.getLogger().info("Web server stopped.");
        }
    }

    private void registerRoutes() {
        app.get("/api/items", ctx -> {
            List<ShopItem> items = itemRepository.findAll();
            List<ItemDto> dtos = items.stream()
                    .map(this::toDto)
                    .collect(Collectors.toList());
            ctx.json(dtos);
        });

        app.get("/api/items/{id}", ctx -> {
            int id = Integer.parseInt(ctx.pathParam("id"));
            itemRepository.findById(id)
                    .map(this::toDto)
                    .ifPresentOrElse(
                            ctx::json,
                            () -> ctx.status(404).json(Map.of("error", "Item not found"))
                    );
        });

        app.get("/api/items/{id}/history", ctx -> {
            int id = Integer.parseInt(ctx.pathParam("id"));
            int limit = ctx.queryParamAsClass(KEY_LIMIT, Integer.class).getOrDefault(100);

            List<PriceHistory> history = itemRepository.getPriceHistory(id, limit);
            List<PriceHistoryDto> dtos = history.stream()
                    .map(h -> new PriceHistoryDto(
                            h.price().doubleValue(),
                            h.buyVolume(),
                            h.sellVolume(),
                            h.bpd().doubleValue(),
                            h.spd().doubleValue(),
                            h.timestamp().toEpochMilli()
                    ))
                    .collect(Collectors.toList());

            ctx.json(dtos);
        });

        app.get("/api/stats", ctx -> {
            List<ShopItem> items = itemRepository.findAll();
            int onlinePlayers = plugin.getServer().getOnlinePlayers().size();

            Map<String, Object> stats = Map.of(
                    "totalItems", items.size(),
                    "onlinePlayers", onlinePlayers,
                    "serverName", plugin.getServer().getName(),
                    KEY_TIMESTAMP, System.currentTimeMillis()
            );

            ctx.json(stats);
        });

        app.get("/api/prices", ctx -> {
            Map<Integer, BigDecimal> prices = marketEngine.getPriceCache();
            Map<Integer, Double> priceDoubles = prices.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            e -> e.getValue().doubleValue()
                    ));
            ctx.json(priceDoubles);
        });

        app.get("/api/spreads", ctx -> {
            Map<Integer, MarketEngine.SpreadResult> spreads = marketEngine.getSpreadCache();
            Map<Integer, Map<String, Double>> spreadDtos = spreads.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            e -> Map.of(
                                    "bpd", e.getValue().bpd().doubleValue(),
                                    "spd", e.getValue().spd().doubleValue()
                            )
                    ));
            ctx.json(spreadDtos);
        });

        app.get("/api/economy/gdp", ctx -> {
            Optional<EconomySnapshot> latest = economyMetricsManager.getLatestSnapshot();
            if (latest.isPresent()) {
                ctx.json(Map.of(
                        "gdp", latest.get().gdp().doubleValue(),
                        KEY_TIMESTAMP, latest.get().timestamp().toEpochMilli()
                ));
            } else {
                ctx.json(Map.of("gdp", 0.0, KEY_TIMESTAMP, System.currentTimeMillis()));
            }
        });

        app.get("/api/economy/inflation", ctx -> {
            Optional<EconomySnapshot> latest = economyMetricsManager.getLatestSnapshot();
            Map<String, Object> result = new HashMap<>();
            if (latest.isPresent()) {
                result.put("averagePriceChange", latest.get().averagePriceChange().doubleValue());
                result.put("label", economyMetricsManager.getInflationLabel());
                result.put(KEY_TIMESTAMP, latest.get().timestamp().toEpochMilli());
            } else {
                result.put("averagePriceChange", 0.0);
                result.put("label", "N/A");
                result.put(KEY_TIMESTAMP, System.currentTimeMillis());
            }
            ctx.json(result);
        });

        app.get("/api/economy/debt", ctx -> {
            Optional<EconomySnapshot> latest = economyMetricsManager.getLatestSnapshot();
            Map<String, Object> result = new HashMap<>();
            if (latest.isPresent()) {
                result.put("totalDebt", latest.get().totalDebt().doubleValue());
                result.put("activeLoans", latest.get().activeLoans());
                result.put("debtPerCapita", economyMetricsManager.getDebtPerCapita().doubleValue());
                result.put(KEY_TIMESTAMP, latest.get().timestamp().toEpochMilli());
            } else {
                result.put("totalDebt", 0.0);
                result.put("activeLoans", 0);
                result.put("debtPerCapita", 0.0);
                result.put(KEY_TIMESTAMP, System.currentTimeMillis());
            }
            ctx.json(result);
        });

        app.get("/api/economy/history", ctx -> {
            int limit = ctx.queryParamAsClass(KEY_LIMIT, Integer.class).getOrDefault(100);
            List<EconomySnapshot> snapshots = snapshotRepository.findRecent(limit);
            List<EconomySnapshotDto> dtos = snapshots.stream()
                    .map(s -> new EconomySnapshotDto(
                            s.gdp().doubleValue(),
                            s.totalDebt().doubleValue(),
                            s.activeLoans(),
                            s.playerCount(),
                            s.averagePriceChange().doubleValue(),
                            s.transactionVolume().doubleValue(),
                            s.timestamp().toEpochMilli()
                    ))
                    .collect(Collectors.toList());
            ctx.json(dtos);
        });

        app.get("/api/economy/trends", ctx -> {
            List<ShopItem> items = itemRepository.findAll();
            List<Map<String, Object>> trends = new ArrayList<>();
            for (ShopItem item : items) {
                MarketEngine.PriceTrend trend = marketEngine.getPriceTrend(item.id());
                Map<String, Object> entry = new HashMap<>();
                entry.put("itemId", item.id());
                entry.put("material", item.material().name());
                entry.put("displayName", item.getDisplayNameOrMaterial());
                entry.put("direction", trend.direction().name());
                entry.put("percentChange", trend.percentChange().doubleValue());
                entry.put("label", trend.label());
                trends.add(entry);
            }
            ctx.json(trends);
        });

        app.get("/api/transactions", ctx -> {
            int limit = ctx.queryParamAsClass(KEY_LIMIT, Integer.class).getOrDefault(50);
            List<Transaction> transactions = transactionRepository.findRecent(Math.min(limit, 200));
            List<TransactionFeedDto> dtos = transactions.stream()
                    .map(this::toTransactionDto)
                    .collect(Collectors.toList());
            ctx.json(dtos);
        });

        app.get("/api/items/{id}/transactions", ctx -> {
            int id = Integer.parseInt(ctx.pathParam("id"));
            int limit = ctx.queryParamAsClass(KEY_LIMIT, Integer.class).getOrDefault(50);
            List<Transaction> transactions = transactionRepository.findByItem(id, Math.min(limit, 200));
            List<TransactionFeedDto> dtos = transactions.stream()
                    .map(this::toTransactionDto)
                    .collect(Collectors.toList());
            ctx.json(dtos);
        });

        app.get("/api/items/{id}/trend", ctx -> {
            int id = Integer.parseInt(ctx.pathParam("id"));
            MarketEngine.PriceTrend.Direction direction = marketEngine.getTrendDirection(id);
            int streak = marketEngine.getTrendStreak(id);
            MarketEngine.PriceTrend trend = marketEngine.getPriceTrend(id);
            ctx.json(Map.of(
                    "direction", direction.name(),
                    "streak", streak,
                    "percentChange", trend.percentChange().doubleValue()
            ));
        });

        app.get("/api/loans", ctx -> {
            List<Loan> loans = loanRepository.findAllActive();
            AtomicInteger index = new AtomicInteger(1);
            List<AnonLoanDto> dtos = loans.stream()
                    .map(loan -> new AnonLoanDto(
                            index.getAndIncrement(),
                            loan.principal().doubleValue(),
                            loan.currentBalance().doubleValue(),
                            loan.interestRate().doubleValue(),
                            loan.status().name(),
                            loan.createdAt().toEpochMilli(),
                            loan.dueDate().toEpochMilli(),
                            loan.isOverdue()
                    ))
                    .collect(Collectors.toList());
            ctx.json(dtos);
        });

        app.get("/api/loans/stats", ctx -> {
            List<Loan> activeLoans = loanRepository.findAllActive();
            int overdueCount = loanRepository.countOverdue();

            BigDecimal totalPrincipal = BigDecimal.ZERO;
            BigDecimal totalBalance = BigDecimal.ZERO;
            BigDecimal totalRate = BigDecimal.ZERO;

            for (Loan loan : activeLoans) {
                totalPrincipal = totalPrincipal.add(loan.principal());
                totalBalance = totalBalance.add(loan.currentBalance());
                totalRate = totalRate.add(loan.interestRate());
            }

            double avgRate = activeLoans.isEmpty() ? 0.0 :
                    totalRate.divide(BigDecimal.valueOf(activeLoans.size()), 4, RoundingMode.HALF_UP).doubleValue();

            ctx.json(new LoanStatsDto(
                    activeLoans.size(),
                    totalPrincipal.doubleValue(),
                    totalBalance.doubleValue(),
                    avgRate,
                    overdueCount
            ));
        });

        // ── Player portfolio ─────────────────────────────────────────────────
        app.get("/api/portfolio/{playerName}", ctx -> {
            String playerName = ctx.pathParam("playerName");
            if (playerName == null || playerName.isBlank()) {
                ctx.status(400).result("playerName is required");
                return;
            }
            portfolioService.buildPortfolio(playerName.trim())
                    .ifPresentOrElse(
                            dto -> ctx.json(dto),
                            () -> ctx.status(404).result("Player not found: " + playerName)
                    );
        });

        app.get("/api/leaderboard", ctx -> {
            int limit = ctx.queryParamAsClass(KEY_LIMIT, Integer.class).getOrDefault(20);
            List<PlayerData> topTraders = playerRepository.findTopTraders(Math.min(limit, 100));
            AtomicInteger rank = new AtomicInteger(1);
            List<LeaderboardEntryDto> dtos = topTraders.stream()
                    .map(p -> new LeaderboardEntryDto(
                            rank.getAndIncrement(),
                            p.username() != null ? p.username() : "Unknown",
                            p.totalTraded().doubleValue(),
                            p.totalBought().doubleValue(),
                            p.totalSold().doubleValue(),
                            p.transactionCount()
                    ))
                    .collect(Collectors.toList());
            ctx.json(dtos);
        });

        app.get("/api/economy/volume-multiplier", ctx -> {
            ctx.json(Map.of(
                    "multiplier", marketEngine.getGlobalVolumeMultiplier(),
                    KEY_TIMESTAMP, System.currentTimeMillis()
            ));
        });

        // ── Market events endpoint ──────────────────────────────────────────
        app.get("/api/events", ctx -> {
            List<MarketEvent> active = marketEventService.getActiveEvents();
            List<MarketEvent> recent = marketEventService.listEvents().stream()
                    .filter(e -> e.status() != com.noahblclarkson.autotune.model.MarketEvent.Status.SCHEDULED)
                    .limit(20)
                    .toList();
            Instant now = Instant.now();
            List<Map<String, Object>> activeDtos = active.stream()
                    .map(e -> Map.<String, Object>of(
                            "id", e.id().toString(),
                            "name", e.name(),
                            "type", e.type().name(),
                            "materials", e.materials(),
                            "multiplier", e.priceMultiplier(),
                            "startsAt", e.startsAt().toEpochMilli(),
                            "endsAt", e.endsAt().toEpochMilli(),
                            "status", e.status().name(),
                            "remainingMinutes", Math.max(0, Duration.between(now, e.endsAt()).toMinutes())
                    ))
                    .toList();
            List<Map<String, Object>> recentDtos = recent.stream()
                    .map(e -> Map.<String, Object>of(
                            "id", e.id().toString(),
                            "name", e.name(),
                            "type", e.type().name(),
                            "materials", e.materials(),
                            "multiplier", e.priceMultiplier(),
                            "startsAt", e.startsAt().toEpochMilli(),
                            "endsAt", e.endsAt().toEpochMilli(),
                            "status", e.status().name()
                    ))
                    .toList();
            ctx.json(Map.of(
                    "active", activeDtos,
                    "recent", recentDtos,
                    "activeCount", active.size(),
                    KEY_TIMESTAMP, System.currentTimeMillis()
            ));
        });

        // ── Admin health endpoint ────────────────────────────────────────────
        app.get("/api/admin/health", ctx -> {
            Instant oneDayAgo = Instant.now().minus(Duration.ofDays(1));

            // Market status
            boolean frozen = marketEngine.isFrozen();

            // Circuit breaker
            LoanManager.CircuitBreakerStatus cb = loanManager.getCircuitBreakerStatus();

            // GDP / debt
            BigDecimal gdp = BigDecimal.ZERO;
            BigDecimal totalDebt = BigDecimal.ZERO;
            int activeLoans = 0;
            if (economyMetricsManager.getLatestSnapshot().isPresent()) {
                EconomySnapshot snap = economyMetricsManager.getLatestSnapshot().get();
                gdp = snap.gdp();
                totalDebt = snap.totalDebt();
                activeLoans = snap.activeLoans();
            }

            // Debt/GDP
            double debtGdpRatio = cb.debtGdpRatio();
            String debtGdpLabel;
            if (debtGdpRatio < 0) {
                debtGdpLabel = "N/A";
            } else if (debtGdpRatio < 3.0) {
                debtGdpLabel = String.format("%.2fx", debtGdpRatio);
            } else if (debtGdpRatio < 10.0) {
                debtGdpLabel = String.format("%.2fx ⚠", debtGdpRatio);
            } else {
                debtGdpLabel = String.format("%.2fx ❌", debtGdpRatio);
            }

            // Buy ratio
            BigDecimal buyVol = transactionRepository.getGlobalBuyVolume(oneDayAgo);
            BigDecimal totalVol = transactionRepository.getGlobalVolume(oneDayAgo);
            double buyPct = 0.0;
            if (totalVol.compareTo(BigDecimal.ZERO) > 0) {
                buyPct = buyVol.divide(totalVol, 4, RoundingMode.HALF_UP).doubleValue() * 100.0;
            }

            // Avg spread
            List<ShopItem> allItems = shopManager.getAllItems();
            double totalBpd = 0, totalSpd = 0;
            for (ShopItem item : allItems) {
                MarketEngine.SpreadResult sp = marketEngine.getSpread(item.id());
                totalBpd += sp.bpd().doubleValue();
                totalSpd += sp.spd().doubleValue();
            }
            double avgBpd = allItems.isEmpty() ? 0 : (totalBpd / allItems.size()) * 100;
            double avgSpd = allItems.isEmpty() ? 0 : (totalSpd / allItems.size()) * 100;

            // Volume multiplier
            double globalMult = marketEngine.getGlobalVolumeMultiplier();

            // Inflation
            String inflationLabel = economyMetricsManager.getInflationLabel();

            // Top volatile items
            List<Map<String, Object>> volatilities = new ArrayList<>();
            List<Map<String, Object>> undersells = new ArrayList<>();
            for (ShopItem item : allItems) {
                List<PriceHistory> history = itemRepository
                        .getPriceHistorySince(item.id(), oneDayAgo, 10);
                if (history.size() < 2) continue;
                BigDecimal newest = history.get(0).price();
                BigDecimal oldest = history.get(history.size() - 1).price();
                if (oldest.compareTo(BigDecimal.ZERO) <= 0) continue;
                double pctChange = newest.subtract(oldest)
                        .divide(oldest, 4, RoundingMode.HALF_UP)
                        .doubleValue() * 100.0;
                Map<String, Object> entry = Map.of(
                        "id", item.id(),
                        "material", item.material().name(),
                        "displayName", item.getDisplayNameOrMaterial(),
                        "pctChange", pctChange
                );
                volatilities.add(entry);
                undersells.add(entry);
            }
            volatilities.sort((a, b) -> {
                double av = (Double) a.get("pctChange");
                double bv = (Double) b.get("pctChange");
                return Double.compare(Math.abs(bv), Math.abs(av)); // most volatile first
            });
            undersells.sort((a, b) -> {
                double av = (Double) a.get("pctChange");
                double bv = (Double) b.get("pctChange");
                return Double.compare(av, bv); // most negative first (undersold)
            });

            Map<String, Object> response = new HashMap<>();
            response.put("frozen", frozen);
            response.put("gdp", gdp.doubleValue());
            response.put("totalDebt", totalDebt.doubleValue());
            response.put("activeLoans", activeLoans);
            response.put("debtGdpRatio", debtGdpRatio);
            response.put("debtGdpLabel", debtGdpLabel);
            response.put("circuitBreakerTier", cb.tier());
            response.put("interestMultiplier", cb.interestMultiplier());
            response.put("buyPct", buyPct);
            response.put("sellPct", 100.0 - buyPct);
            response.put("avgBpd", avgBpd);
            response.put("avgSpd", avgSpd);
            response.put("globalVolumeMultiplier", globalMult);
            response.put("inflationLabel", inflationLabel);
            response.put("topVolatile", volatilities.stream().limit(5).collect(Collectors.toList()));
            response.put("topUndersold", undersells.stream().limit(5).collect(Collectors.toList()));
            response.put("timestamp", System.currentTimeMillis());
            ctx.json(response);
        });

        app.exception(Exception.class, (e, ctx) -> {
            plugin.getLogger().log(Level.WARNING, "Web API error: " + e.getMessage());
            ctx.status(500).json(Map.of("error", "Internal server error"));
        });
    }

    private void registerWebSocket() {
        app.ws("/ws/market", ws -> {
            ws.onConnect(ctx -> {
                wsClients.add(ctx);
                ctx.send(gson.toJson(Map.of("type", "connected", "message", "Connected to market feed")));
            });

            ws.onClose(ctx -> wsClients.remove(ctx));

            ws.onError(ctx -> {
                wsClients.remove(ctx);
                plugin.getLogger().log(Level.WARNING, "WebSocket error: " + ctx.error());
            });
        });
    }

    public void broadcastPriceUpdate(Map<Integer, BigDecimal> prices) {
        if (wsClients.isEmpty()) {
            return;
        }

        Map<String, Object> message = Map.of(
                "type", "price_update",
                KEY_TIMESTAMP, System.currentTimeMillis(),
                "prices", prices.entrySet().stream()
                        .collect(Collectors.toMap(
                                e -> String.valueOf(e.getKey()),
                                e -> e.getValue().doubleValue()
                        ))
        );

        final String json = gson.toJson(message);
        wsClients.removeIf(ctx -> {
            try {
                ctx.send(json);
                return false;
            } catch (RuntimeException e) {
                // Dead or disconnected WebSocket — remove from active set
                return true;
            }
        });
    }

    private TransactionFeedDto toTransactionDto(Transaction tx) {
        String itemName = itemRepository.findById(tx.itemId())
                .map(ShopItem::getDisplayNameOrMaterial)
                .orElse("Unknown");
        return new TransactionFeedDto(
                tx.id(),
                tx.itemId(),
                itemName,
                tx.type().name(),
                tx.amount(),
                tx.pricePerUnit().doubleValue(),
                tx.totalPrice().doubleValue(),
                tx.timestamp().toEpochMilli()
        );
    }

    private ItemDto toDto(ShopItem item) {
        MarketEngine.SpreadResult spread = marketEngine.getSpread(item.id());
        return new ItemDto(
                item.id(),
                item.material().name(),
                item.getDisplayNameOrMaterial(),
                item.price().doubleValue(),
                marketEngine.getBuyPrice(item).doubleValue(),
                marketEngine.getSellPrice(item).doubleValue(),
                spread.bpd().doubleValue(),
                spread.spd().doubleValue(),
                item.section(),
                item.buyable(),
                item.itemData() != null,
                marketEngine.get24hChange(item.id()).doubleValue()
        );
    }

    public record ItemDto(
            int id,
            String material,
            String displayName,
            double price,
            double buyPrice,
            double sellPrice,
            double bpd,
            double spd,
            String section,
            Boolean buyable,
            boolean hasCustomData,
            double change24h
    ) {
    }

    public record PriceHistoryDto(
            double price,
            int buyVolume,
            int sellVolume,
            double bpd,
            double spd,
            long timestamp
    ) {
    }

    public record EconomySnapshotDto(
            double gdp,
            double totalDebt,
            int activeLoans,
            int playerCount,
            double averagePriceChange,
            double transactionVolume,
            long timestamp
    ) {
    }

    public record TransactionFeedDto(
            long id,
            int itemId,
            String itemName,
            String type,
            int amount,
            double pricePerUnit,
            double totalPrice,
            long timestamp
    ) {
    }

    public record AnonLoanDto(
            int index,
            double principal,
            double balance,
            double rate,
            String status,
            long createdAt,
            long dueDate,
            boolean overdue
    ) {
    }

    public record LoanStatsDto(
            int totalActive,
            double totalPrincipal,
            double totalBalance,
            double avgRate,
            int overdueCount
    ) {
    }

    public record LeaderboardEntryDto(
            int rank,
            String username,
            double totalTraded,
            double totalBought,
            double totalSold,
            int transactionCount
    ) {
    }
}
