package com.noahblclarkson.autotune.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.config.AutoTuneConfig;
import com.noahblclarkson.autotune.config.AutoTuneConfig.WebConfig;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.database.BadgeRepository;
import com.noahblclarkson.autotune.database.CircuitEventRepository;
import com.noahblclarkson.autotune.database.EconomySnapshotRepository;
import com.noahblclarkson.autotune.database.ItemRepository;
import com.noahblclarkson.autotune.database.LoanRepository;
import com.noahblclarkson.autotune.database.PlayerRepository;
import com.noahblclarkson.autotune.database.TransactionRepository;
import com.noahblclarkson.autotune.database.AuctionRepository;
import com.noahblclarkson.autotune.database.ShopFavoriteRepository;
import com.noahblclarkson.autotune.database.WatchedAuctionRepository;
import com.noahblclarkson.autotune.economy.EconomyManager;
import com.noahblclarkson.autotune.economy.LoanManager;
import com.noahblclarkson.autotune.manager.EconomyMetricsManager;
import com.noahblclarkson.autotune.manager.MarketEngine;
import com.noahblclarkson.autotune.manager.MarketEventService;
import com.noahblclarkson.autotune.manager.PriceAlertManager;
import com.noahblclarkson.autotune.manager.ShopManager;
import com.noahblclarkson.autotune.model.BadgeDto;
import com.noahblclarkson.autotune.model.CircuitEvent;
import com.noahblclarkson.autotune.model.MarketEvent;
import com.noahblclarkson.autotune.model.EconomySnapshot;
import com.noahblclarkson.autotune.model.Loan;
import com.noahblclarkson.autotune.model.PlayerData;
import com.noahblclarkson.autotune.model.PriceAlert;
import com.noahblclarkson.autotune.model.PriceHistory;
import com.noahblclarkson.autotune.model.ShopItem;
import com.noahblclarkson.autotune.model.PriceChangeDto;
import com.noahblclarkson.autotune.model.PnLHistoryDto;
import com.noahblclarkson.autotune.model.PortfolioDto;
import com.noahblclarkson.autotune.model.Transaction;
import com.noahblclarkson.autotune.model.AuctionOrder;
import com.noahblclarkson.autotune.model.AuctionFill;
import com.noahblclarkson.autotune.service.AdminAuditService;
import com.noahblclarkson.autotune.service.PlayerImpactService;
import com.noahblclarkson.autotune.service.PlayerStreakService;
import com.noahblclarkson.autotune.service.PortfolioService;
import com.noahblclarkson.autotune.service.EconomyWhatMovedService;
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
import java.util.Locale;
import java.util.logging.Level;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Singleton
@SuppressWarnings("PMD")
public class WebServer {

    // API response field names
    private static final String KEY_LIMIT = "limit";
    private static final String KEY_TIMESTAMP = "timestamp";
    private static final String KEY_ERROR = "error";
    // Threshold constants for market attribution logic
    private static final double MARKET_EVENT_THRESHOLD = 2.0;
    private static final double HIGH_VOLUME_THRESHOLD = 2.5;
    private static final double SIGNIFICANT_MOVE_THRESHOLD = 5.0;
    private static final double STABLE_PRICE_THRESHOLD = 0.5;
    private static final double DEBT_GDP_CAUTION = 3.0;
    private static final double DEBT_GDP_DANGER = 10.0;
    private static final double DEBT_GDP_MIN = 0.0;
    private static final double PRICE_CHANGE_ZERO = 0.0;
    // Repeated message strings
    private static final String MSG_UNKNOWN = "Unknown";
    private static final String MSG_ITEM_NOT_FOUND_STANDALONE = "Item not found";
    private static final String MSG_PLAYER_NAME_REQUIRED = "playerName is required";
    private static final String MSG_PLAYER_NAME_QUERY_REQUIRED = "playerName query param is required";
    private static final String MSG_PLAYER_NOT_FOUND = "Player not found: ";
    private static final String MSG_ITEM_NOT_FOUND = "Item not found: ";
    private static final String MSG_ALERT_NOT_FOUND_OR_NOT_OWNED = "Alert not found or not owned by player";
    private static final String KEY_PLAYER_NAME = "playerName";
    private static final String KEY_ITEM_ID = "itemId";
    private static final String KEY_ITEM_NAME = "itemName";
    private static final String KEY_AMOUNT = "amount";
    private static final String KEY_LABEL = "label";
    private static final String KEY_PCT_CHANGE = "pctChange";
    private static final String KEY_CURRENT = "current";
    private static final String KEY_DEFAULT = "default";
    private static final String KEY_RANGE_MIN = "rangeMin";
    private static final String KEY_RANGE_MAX = "rangeMax";
    private static final String KEY_UNIT = "unit";
    private static final String KEY_TYPE = "type";
    private static final String KEY_PRICE = "price";
    private static final String KEY_MATERIAL = "material";
    // Repeated message strings

    private final AutoTune plugin;
    private final ConfigManager configManager;
    private final ItemRepository itemRepository;
    private final MarketEngine marketEngine;
    private final EconomySnapshotRepository snapshotRepository;
    private final CircuitEventRepository circuitEventRepository;
    private final EconomyMetricsManager economyMetricsManager;
    private final TransactionRepository transactionRepository;
    private final LoanRepository loanRepository;
    private final PlayerRepository playerRepository;
    private final BadgeRepository badgeRepository;
    private final ShopFavoriteRepository shopFavoriteRepository;
    private final LoanManager loanManager;
    private final ShopManager shopManager;
    private final EconomyManager economyManager;
    private final MarketEventService marketEventService;
    private final PriceAlertManager priceAlertManager;
    private final Server server;
    private final PortfolioService portfolioService;
    private final PlayerImpactService playerImpactService;
    private final PlayerStreakService streakService;
    private final EconomyWhatMovedService whatMovedService;
    private final AdminAuditService auditService;
    private final AuctionRepository auctionRepository;
    private final WatchedAuctionRepository watchedAuctionRepository;
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
            CircuitEventRepository circuitEventRepository,
            EconomyMetricsManager economyMetricsManager,
            TransactionRepository transactionRepository,
            LoanRepository loanRepository,
            PlayerRepository playerRepository,
            BadgeRepository badgeRepository,
            ShopFavoriteRepository shopFavoriteRepository,
            LoanManager loanManager,
            ShopManager shopManager,
            EconomyManager economyManager,
            MarketEventService marketEventService,
            PriceAlertManager priceAlertManager,
            Server server,
            PlayerStreakService streakService,
            EconomyWhatMovedService whatMovedService,
            AdminAuditService auditService,
            AuctionRepository auctionRepository,
            WatchedAuctionRepository watchedAuctionRepository
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.itemRepository = itemRepository;
        this.marketEngine = marketEngine;
        this.snapshotRepository = snapshotRepository;
        this.circuitEventRepository = circuitEventRepository;
        this.economyMetricsManager = economyMetricsManager;
        this.transactionRepository = transactionRepository;
        this.loanRepository = loanRepository;
        this.playerRepository = playerRepository;
        this.badgeRepository = badgeRepository;
        this.shopFavoriteRepository = shopFavoriteRepository;
        this.loanManager = loanManager;
        this.shopManager = shopManager;
        this.economyManager = economyManager;
        this.marketEventService = marketEventService;
        this.priceAlertManager = priceAlertManager;
        this.server = server;
        this.portfolioService = new PortfolioService(
                playerRepository, itemRepository, loanRepository, transactionRepository, economyManager, server);
        this.playerImpactService = new PlayerImpactService(
                playerRepository, itemRepository, transactionRepository, marketEngine);
        this.streakService = streakService;
        this.whatMovedService = whatMovedService;
        this.auditService = auditService;
        this.auctionRepository = auctionRepository;
        this.watchedAuctionRepository = watchedAuctionRepository;
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
    }

    public void start() {
        WebConfig config = configManager.getConfig().web();

        app = Javalin.create(javalinConfig -> {
            javalinConfig.showJavalinBanner = false;

            // Browser requests with many cookies/headers can exceed Jetty's 8 KB default.
            javalinConfig.jetty.modifyHttpConfiguration(httpConfig -> {
                httpConfig.setRequestHeaderSize(16384);
                httpConfig.setResponseHeaderSize(16384);
            });

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
            ctx.header("Access-Control-Allow-Methods", "GET, POST, PATCH, DELETE, OPTIONS");
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
                            () -> ctx.status(404).json(Map.of(KEY_ERROR, MSG_ITEM_NOT_FOUND_STANDALONE))
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

        // ── Price attribution endpoint ──────────────────────────────────────
        // Returns price history with "what moved this price" attribution per point.
        app.get("/api/items/{id}/attribution", ctx -> {
            int id = Integer.parseInt(ctx.pathParam("id"));
            int limit = ctx.queryParamAsClass(KEY_LIMIT, Integer.class).getOrDefault(50);

            List<PriceHistory> history = itemRepository.getPriceHistory(id, limit);
            if (history.isEmpty()) {
                ctx.json(List.of());
                return;
            }

            String materialName = itemRepository.findById(id)
                    .map(item -> item.material().name())
                    .orElse("");
            List<MarketEvent> activeEvents = marketEventService.getActiveEvents();

            // Compute average volume for volume-vs-normal ratio
            double avgVol = history.stream()
                    .mapToInt(PriceHistory::totalVolume)
                    .average()
                    .orElse(1.0);

            List<PriceChangeDto> dtos = new ArrayList<>();
            for (int i = 0; i < history.size(); i++) {
                PriceHistory curr = history.get(i);
                PriceHistory prev = i > 0 ? history.get(i - 1) : null;

                double currPrice = curr.price().doubleValue();
                double prevPrice = prev != null ? prev.price().doubleValue() : currPrice;
                double pctChange = prevPrice > 0
                        ? ((currPrice - prevPrice) / prevPrice) * 100.0
                        : 0.0;
                double volRatio = avgVol > 0 ? curr.totalVolume() / avgVol : 1.0;

                // Find active event multiplier for this item at this time
                double eventMult = 1.0;
                boolean hasEvent = false;
                String eventName = null;
                Instant pointTime = curr.timestamp();
                for (MarketEvent evt : activeEvents) {
                    if (evt.isActive(pointTime) && evt.matchesMaterial(materialName)) {
                        eventMult = evt.priceMultiplier();
                        hasEvent = true;
                        eventName = evt.name();
                        break;
                    }
                }

                // Attribution logic
                String attribution;
                String key;
                if (hasEvent && Math.abs(pctChange) > MARKET_EVENT_THRESHOLD) {
                    attribution = "Due to market event: " + (eventName != null ? eventName : "active event");
                    key = "EVENT";
                } else if (volRatio > HIGH_VOLUME_THRESHOLD) {
                    attribution = String.format("High volume spike (%.1f× normal)", volRatio);
                    key = "VOLUME";
                } else if (Math.abs(pctChange) > SIGNIFICANT_MOVE_THRESHOLD) {
                    attribution = String.format("Significant price movement (%.1f%%)", pctChange);
                    key = "TREND";
                } else if (Math.abs(pctChange) < STABLE_PRICE_THRESHOLD) {
                    attribution = "Stable price — minimal change";
                    key = "STABLE";
                } else {
                    attribution = "Normal market activity";
                    key = "NORMAL";
                }

                dtos.add(new PriceChangeDto(
                        curr.timestamp().toEpochMilli(),
                        currPrice,
                        prevPrice,
                        pctChange,
                        curr.bpd().doubleValue(),
                        curr.spd().doubleValue(),
                        curr.totalVolume(),
                        volRatio,
                        eventMult,
                        attribution,
                        key,
                        hasEvent
                ));
            }

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
                result.put(KEY_LABEL, economyMetricsManager.getInflationLabel());
                result.put(KEY_TIMESTAMP, latest.get().timestamp().toEpochMilli());
            } else {
                result.put("averagePriceChange", 0.0);
                result.put(KEY_LABEL, "N/A");
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

        app.get("/api/economy/circuit-events", ctx -> {
            int limit = ctx.queryParamAsClass(KEY_LIMIT, Integer.class).getOrDefault(100);
            List<CircuitEventDto> dtos = circuitEventRepository.findRecent(limit).stream()
                    .map(e -> new CircuitEventDto(
                            e.id(),
                            e.previousTier(),
                            e.newTier(),
                            e.debtGdpRatio(),
                            e.gdp().doubleValue(),
                            e.totalDebt().doubleValue(),
                            e.interestMultiplier(),
                            e.adminInitiated(),
                            e.details(),
                            e.timestamp().toEpochMilli()
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
                entry.put(KEY_ITEM_ID, item.id());
                entry.put(KEY_MATERIAL, item.material().name());
                entry.put("displayName", item.getDisplayNameOrMaterial());
                entry.put("direction", trend.direction().name());
                entry.put("percentChange", trend.percentChange().doubleValue());
                entry.put(KEY_LABEL, trend.label());
                trends.add(entry);
            }
            ctx.json(trends);
        });

        // GET /api/economy/what-moved — natural language price move explanations
        app.get("/api/economy/what-moved", ctx -> {
            List<EconomyWhatMovedService.WhatMovedEntry> entries = whatMovedService.getTopMovers();
            ctx.json(entries);
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
                    "percentChange", trend.percentChange().doubleValue(),
                    "projected24h", trend.projected24h().doubleValue()
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

        // ── Auction house ─────────────────────────────────────────────────────
        // GET /api/auction/stats — auction statistics
        app.get("/api/auction/stats", ctx -> {
            long orderCount = auctionRepository.countOrders();
            long fillCount = auctionRepository.countFills();
            List<AuctionOrder> activeOrders = auctionRepository.findAllActive();
            // Best bid/ask per material for summary
            Map<String, Map<String, BigDecimal>> bookSummary = new HashMap<>();
            for (AuctionOrder order : activeOrders) {
                bookSummary.computeIfAbsent(order.material(), k -> new HashMap<>());
                Map<String, BigDecimal> side = bookSummary.get(order.material());
                if (order.side() == AuctionOrder.OrderSide.BUY) {
                    side.merge("bestBid", order.price(), (a, b) -> a.compareTo(b) > 0 ? a : b);
                    side.merge("bidCount", BigDecimal.ONE, BigDecimal::add);
                } else {
                    side.merge("bestAsk", order.price(), (a, b) -> a.compareTo(b) < 0 ? a : b);
                    side.merge("askCount", BigDecimal.ONE, BigDecimal::add);
                }
            }
            // Recent fills
            List<AuctionFill> recentFills = auctionRepository.findRecentFills(5);
            List<Map<String, Object>> recentFillsDto = recentFills.stream()
                    .map(f -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("id", f.id().toString());
                        m.put("quantity", f.quantity());
                        m.put("price", f.price().doubleValue());
                        m.put("filledAt", f.filledAt().toEpochMilli());
                        return m;
                    })
                    .collect(Collectors.toList());

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalOrders", orderCount);
            stats.put("totalFills", fillCount);
            stats.put("activeOrders", activeOrders.size());
            stats.put("materialsWithOrders", bookSummary.size());
            stats.put("bookSummary", bookSummary);
            stats.put("recentFills", recentFillsDto);
            ctx.json(stats);
        });

        // GET /api/auction/orders — all active orders (optionally by material)
        app.get("/api/auction/orders", ctx -> {
            String material = ctx.queryParam("material");
            List<AuctionOrder> orders;
            if (material != null && !material.isBlank()) {
                orders = auctionRepository.findActiveByMaterial(material.toUpperCase());
            } else {
                orders = auctionRepository.findAllActive();
            }
            List<Map<String, Object>> dtos = orders.stream()
                    .map(this::toAuctionOrderDto)
                    .collect(Collectors.toList());
            ctx.json(dtos);
        });

        // GET /api/auction/orders/{orderId} — single order
        app.get("/api/auction/orders/{orderId}", ctx -> {
            String idStr = ctx.pathParam("orderId");
            try {
                UUID orderId = UUID.fromString(idStr);
                Optional<AuctionOrder> opt = auctionRepository.findById(orderId);
                if (opt.isEmpty()) {
                    ctx.status(404).result("Order not found: " + idStr);
                    return;
                }
                ctx.json(toAuctionOrderDto(opt.get()));
            } catch (IllegalArgumentException e) {
                ctx.status(400).result("Invalid order ID format: " + idStr);
            }
        });

        // GET /api/auction/fills — recent fills
        app.get("/api/auction/fills", ctx -> {
            int limit = ctx.queryParamAsClass(KEY_LIMIT, Integer.class).getOrDefault(50);
            int cappedLimit = Math.min(limit, 200);
            List<AuctionFill> fills = auctionRepository.findRecentFills(cappedLimit);
            List<Map<String, Object>> dtos = fills.stream()
                    .map(f -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("id", f.id().toString());
                        m.put("buyOrderId", f.buyOrderId().toString());
                        m.put("sellOrderId", f.sellOrderId().toString());
                        m.put("quantity", f.quantity());
                        m.put("price", f.price().doubleValue());
                        m.put("total", f.price().multiply(BigDecimal.valueOf(f.quantity())).doubleValue());
                        m.put("filledAt", f.filledAt().toEpochMilli());
                        return m;
                    })
                    .collect(Collectors.toList());
            ctx.json(dtos);
        });

        // GET /api/auction/fills/{orderId} — fills for a specific order
        app.get("/api/auction/fills/{orderId}", ctx -> {
            String idStr = ctx.pathParam("orderId");
            try {
                UUID orderId = UUID.fromString(idStr);
                List<AuctionFill> fills = auctionRepository.findFillsByOrder(orderId);
                List<Map<String, Object>> dtos = fills.stream()
                        .map(f -> {
                            Map<String, Object> m = new HashMap<>();
                            m.put("id", f.id().toString());
                            m.put("quantity", f.quantity());
                            m.put("price", f.price().doubleValue());
                            m.put("total", f.price().multiply(BigDecimal.valueOf(f.quantity())).doubleValue());
                            m.put("filledAt", f.filledAt().toEpochMilli());
                            return m;
                        })
                        .collect(Collectors.toList());
                ctx.json(dtos);
            } catch (IllegalArgumentException e) {
                ctx.status(400).result("Invalid order ID format: " + idStr);
            }
        });

        // GET /api/auction/fill-rate?days=N — fill count per day for sparklines
        app.get("/api/auction/fill-rate", ctx -> {
            int days = ctx.queryParamAsClass("days", Integer.class).getOrDefault(7);
            int cappedDays = Math.min(Math.max(days, 1), 30);
            List<AuctionRepository.DayFillCount> daily = auctionRepository.findFillsByDay(cappedDays);
            List<Map<String, Object>> dtos = daily.stream()
                    .map(d -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("date", d.date());
                        m.put("count", d.count());
                        return m;
                    })
                    .collect(Collectors.toList());
            ctx.json(dtos);
        });

        // GET /api/auction/player/{playerName} — player's active orders
        app.get("/api/auction/player/{playerName}", ctx -> {
            String playerName = ctx.pathParam(KEY_PLAYER_NAME);
            if (playerName == null || playerName.isBlank()) {
                ctx.status(400).result(MSG_PLAYER_NAME_REQUIRED);
                return;
            }
            PlayerData player = playerRepository.findByName(playerName.trim()).orElse(null);
            if (player == null) {
                ctx.status(404).result(MSG_PLAYER_NOT_FOUND + playerName);
                return;
            }
            List<AuctionOrder> orders = auctionRepository.findActiveByPlayer(player.uuid());
            List<Map<String, Object>> dtos = orders.stream()
                    .map(this::toAuctionOrderDto)
                    .collect(Collectors.toList());
            ctx.json(dtos);
        });

        // GET /api/auction/materials — list of materials with active orders, with best bid/ask
        app.get("/api/auction/materials", ctx -> {
            List<AuctionOrder> all = auctionRepository.findAllActive();
            Map<String, Map<String, Object>> summary = new LinkedHashMap<>();
            for (AuctionOrder order : all) {
                summary.computeIfAbsent(order.material(), mat -> {
                    Map<String, Object> s = new HashMap<>();
                    s.put("material", mat);
                    s.put("totalOrders", 0);
                    s.put("buyOrders", 0);
                    s.put("sellOrders", 0);
                    s.put("bestBid", null);
                    s.put("bestAsk", null);
                    return s;
                });
                Map<String, Object> s = summary.get(order.material());
                s.put("totalOrders", ((Number) s.get("totalOrders")).intValue() + 1);
                if (order.side() == AuctionOrder.OrderSide.BUY) {
                    s.put("buyOrders", ((Number) s.get("buyOrders")).intValue() + 1);
                    BigDecimal currentBest = (BigDecimal) s.get("bestBid");
                    if (currentBest == null || order.price().compareTo(currentBest) > 0) {
                        s.put("bestBid", order.price().doubleValue());
                    }
                } else {
                    s.put("sellOrders", ((Number) s.get("sellOrders")).intValue() + 1);
                    BigDecimal currentBest = (BigDecimal) s.get("bestAsk");
                    if (currentBest == null || order.price().compareTo(currentBest) < 0) {
                        s.put("bestAsk", order.price().doubleValue());
                    }
                }
            }
            ctx.json(new ArrayList<>(summary.values()));
        });

        // GET /api/auction/depth?material=X — depth chart data (top-N bids and asks for a material)
        app.get("/api/auction/depth", ctx -> {
            String material = ctx.queryParam("material");
            if (material == null || material.isBlank()) {
                ctx.status(400).result("material query param is required");
                return;
            }
            int depth = ctx.queryParamAsClass("depth", Integer.class).getOrDefault(5);
            int cappedDepth = Math.min(Math.max(depth, 1), 20);
            Map<String, List<AuctionOrder>> depthMap = auctionRepository.findDepthByMaterial(material.toUpperCase(), cappedDepth);
            List<Map<String, Object>> bids = depthMap.getOrDefault("bids", List.of()).stream()
                    .map(o -> {
                        Map<String, Object> m = new java.util.HashMap<>();
                        m.put("id", o.id().toString());
                        m.put("price", o.price().doubleValue());
                        m.put("remainingQuantity", o.remainingQuantity());
                        m.put("totalValue", o.price().multiply(BigDecimal.valueOf(o.remainingQuantity())).doubleValue());
                        return m;
                    })
                    .collect(Collectors.toList());
            List<Map<String, Object>> asks = depthMap.getOrDefault("asks", List.of()).stream()
                    .map(o -> {
                        Map<String, Object> m = new java.util.HashMap<>();
                        m.put("id", o.id().toString());
                        m.put("price", o.price().doubleValue());
                        m.put("remainingQuantity", o.remainingQuantity());
                        m.put("totalValue", o.price().multiply(BigDecimal.valueOf(o.remainingQuantity())).doubleValue());
                        return m;
                    })
                    .collect(Collectors.toList());
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("material", material.toUpperCase());
            result.put("bids", bids);
            result.put("asks", asks);
            result.put("depth", cappedDepth);
            ctx.json(result);
        });

        // POST /api/auction/orders/{orderId}/watch?playerName=X — watch an order
        app.post("/api/auction/orders/{orderId}/watch", ctx -> {
            String orderIdStr = ctx.pathParam("orderId");
            String playerName = ctx.queryParam("playerName");
            if (playerName == null || playerName.isBlank()) {
                ctx.status(400).json(Map.of(KEY_ERROR, MSG_PLAYER_NAME_REQUIRED));
                return;
            }
            try {
                UUID orderId = UUID.fromString(orderIdStr);
                PlayerData player = playerRepository.findByName(playerName.trim()).orElse(null);
                if (player == null) {
                    ctx.status(404).json(Map.of(KEY_ERROR, MSG_PLAYER_NOT_FOUND + playerName));
                    return;
                }
                if (auctionRepository.findById(orderId).isEmpty()) {
                    ctx.status(404).json(Map.of(KEY_ERROR, "Order not found: " + orderIdStr));
                    return;
                }
                watchedAuctionRepository.watch(player.uuid(), orderId, "FILLED");
                ctx.status(201).json(Map.of("watching", true, "orderId", orderIdStr));
            } catch (IllegalArgumentException e) {
                ctx.status(400).json(Map.of(KEY_ERROR, "Invalid order ID format: " + orderIdStr));
            }
        });

        // DELETE /api/auction/orders/{orderId}/watch?playerName=X — stop watching an order
        app.delete("/api/auction/orders/{orderId}/watch", ctx -> {
            String orderIdStr = ctx.pathParam("orderId");
            String playerName = ctx.queryParam("playerName");
            if (playerName == null || playerName.isBlank()) {
                ctx.status(400).json(Map.of(KEY_ERROR, MSG_PLAYER_NAME_REQUIRED));
                return;
            }
            try {
                UUID orderId = UUID.fromString(orderIdStr);
                PlayerData player = playerRepository.findByName(playerName.trim()).orElse(null);
                if (player == null) {
                    ctx.status(404).json(Map.of(KEY_ERROR, MSG_PLAYER_NOT_FOUND + playerName));
                    return;
                }
                watchedAuctionRepository.unwatch(player.uuid(), orderId);
                ctx.status(204).result("");
            } catch (IllegalArgumentException e) {
                ctx.status(400).json(Map.of(KEY_ERROR, "Invalid order ID format: " + orderIdStr));
            }
        });

        // GET /api/auction/orders/{orderId}/watch?playerName=X — check if watching an order
        app.get("/api/auction/orders/{orderId}/watch", ctx -> {
            String orderIdStr = ctx.pathParam("orderId");
            String playerName = ctx.queryParam("playerName");
            if (playerName == null || playerName.isBlank()) {
                ctx.status(400).json(Map.of(KEY_ERROR, MSG_PLAYER_NAME_REQUIRED));
                return;
            }
            try {
                UUID orderId = UUID.fromString(orderIdStr);
                PlayerData player = playerRepository.findByName(playerName.trim()).orElse(null);
                if (player == null) {
                    ctx.status(404).json(Map.of(KEY_ERROR, MSG_PLAYER_NOT_FOUND + playerName));
                    return;
                }
                boolean watching = watchedAuctionRepository.isWatching(player.uuid(), orderId);
                ctx.json(Map.of("watching", watching, "orderId", orderIdStr));
            } catch (IllegalArgumentException e) {
                ctx.status(400).json(Map.of(KEY_ERROR, "Invalid order ID format: " + orderIdStr));
            }
        });

        // ── Player portfolio ─────────────────────────────────────────────────
        app.get("/api/portfolio/{playerName}", ctx -> {
            String playerName = ctx.pathParam(KEY_PLAYER_NAME);
            if (playerName == null || playerName.isBlank()) {
                ctx.status(400).result(MSG_PLAYER_NAME_REQUIRED);
                return;
            }
            portfolioService.buildPortfolio(playerName.trim())
                    .ifPresentOrElse(
                            dto -> ctx.json(dto),
                            () -> ctx.status(404).result(MSG_PLAYER_NOT_FOUND + playerName)
                    );
        });

        app.get("/api/portfolio/{playerName}/transactions", ctx -> {
            String playerName = ctx.pathParam(KEY_PLAYER_NAME);
            int limit = ctx.queryParamAsClass(KEY_LIMIT, Integer.class).getOrDefault(50);
            if (playerName == null || playerName.isBlank()) {
                ctx.status(400).result(MSG_PLAYER_NAME_REQUIRED);
                return;
            }
            PlayerData player = playerRepository.findByName(playerName.trim()).orElse(null);
            if (player == null) {
                ctx.status(404).result(MSG_PLAYER_NOT_FOUND + playerName);
                return;
            }
            List<Transaction> transactions = transactionRepository.findByPlayer(
                    player.uuid(), Math.min(limit, 200));
            List<TransactionFeedDto> dtos = transactions.stream()
                    .map(this::toTransactionDto)
                    .collect(Collectors.toList());
            ctx.json(dtos);
        });


        app.get("/api/portfolio/{playerName}/transactions.csv", ctx -> {
            String playerName = ctx.pathParam(KEY_PLAYER_NAME);
            if (playerName == null || playerName.isBlank()) {
                ctx.status(400).result(MSG_PLAYER_NAME_REQUIRED);
                return;
            }
            PlayerData player = playerRepository.findByName(playerName.trim()).orElse(null);
            if (player == null) {
                ctx.status(404).result(MSG_PLAYER_NOT_FOUND + playerName);
                return;
            }

            // Parse optional from/to query params (ISO-8601 timestamps)
            String fromStr = ctx.queryParam("from");
            String toStr = ctx.queryParam("to");
            String limitStr = ctx.queryParam("limit");
            int limit = Math.min(Integer.parseInt(limitStr != null ? limitStr : "1000"), 10000);


            List<Transaction> transactions;
            if (fromStr != null && toStr != null) {
                Instant from = Instant.parse(fromStr);
                Instant to = Instant.parse(toStr);
                transactions = transactionRepository.findByPlayerRange(player.uuid(), from, to, limit);
            } else {
                transactions = transactionRepository.findByPlayer(player.uuid(), limit);
            }

            StringBuilder csv = new StringBuilder();
            csv.append("date,type,material,quantity,pricePerUnit,totalValue\n");
            for (Transaction tx : transactions) {
                String itemName = itemRepository.findById(tx.itemId())
                        .map(ShopItem::getDisplayNameOrMaterial)
                        .orElse(MSG_UNKNOWN);
                csv.append(tx.timestamp()).append(',')          // ISO-8601
                        .append(tx.type().name()).append(',')
                        .append(itemName).append(',')
                        .append(tx.amount()).append(',')
                        .append(tx.pricePerUnit()).append(',')
                        .append(tx.totalPrice()).append('\n');
            }

            String filename = "autotune-trades-" + playerName.trim() + ".csv";
            ctx.contentType("text/csv")
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .result(csv.toString());
        });

        app.get("/api/portfolio/{playerName}/pnl-history", ctx -> {
            String playerName = ctx.pathParam(KEY_PLAYER_NAME);
            if (playerName == null || playerName.isBlank()) {
                ctx.status(400).result(MSG_PLAYER_NAME_REQUIRED);
                return;
            }
            List<PnLHistoryDto> history = portfolioService.getPnlHistory(playerName.trim());
            ctx.json(history);
        });

        // ── Player market impact ─────────────────────────────────────────────
        app.get("/api/portfolio/{playerName}/market-impact", ctx -> {
            String playerName = ctx.pathParam(KEY_PLAYER_NAME);
            if (playerName == null || playerName.isBlank()) {
                ctx.status(400).result(MSG_PLAYER_NAME_REQUIRED);
                return;
            }
            playerImpactService.compute(playerName.trim())
                    .ifPresentOrElse(
                            dto -> ctx.json(dto),
                            () -> ctx.status(404).result(MSG_PLAYER_NOT_FOUND + playerName)
                    );
        });

        app.get("/api/leaderboard", ctx -> {
            int limit = ctx.queryParamAsClass(KEY_LIMIT, Integer.class).getOrDefault(20);
            int cappedLimit = Math.min(limit, 100);
            String period = ctx.queryParam("period");
            List<LeaderboardEntryDto> dtos;

            if (period != null && !period.equals("all")) {
                // Period-filtered leaderboard — aggregate from transactions table
                List<TransactionRepository.TransactionPeriodAggregate> aggregates =
                        transactionRepository.findTopTradersByPeriod(period, cappedLimit);
                AtomicInteger rank = new AtomicInteger(1);
                dtos = aggregates.stream()
                        .map(a -> {
                            double totalTraded = a.totalBought() + a.totalSold();
                            // netTrade = earnings (sells) - spending (buys); negative = net buyer
                            double netTrade = a.totalEarned().subtract(a.totalSpent()).doubleValue();
                            return new LeaderboardEntryDto(
                                    rank.getAndIncrement(),
                                    a.username() != null ? a.username() : MSG_UNKNOWN,
                                    totalTraded,
                                    (double) a.totalBought(),
                                    (double) a.totalSold(),
                                    (int) a.transactionCount()
                            );
                        })
                        .collect(Collectors.toList());
            } else {
                // All-time leaderboard — use persisted cumulative counters
                List<PlayerData> topTraders = playerRepository.findTopTraders(cappedLimit);
                AtomicInteger rank = new AtomicInteger(1);
                dtos = topTraders.stream()
                        .map(p -> new LeaderboardEntryDto(
                                rank.getAndIncrement(),
                                p.username() != null ? p.username() : MSG_UNKNOWN,
                                p.totalTraded().doubleValue(),
                                p.totalBought().doubleValue(),
                                p.totalSold().doubleValue(),
                                p.transactionCount()
                        ))
                        .collect(Collectors.toList());
            }
            ctx.json(dtos);
        });

        // GET /api/badges/player/{playerName} — earned badges with earn dates for a player
        app.get("/api/badges/player/{playerName}", ctx -> {
            String playerName = ctx.pathParam(KEY_PLAYER_NAME);
            if (playerName == null || playerName.isBlank()) {
                ctx.status(400).result(MSG_PLAYER_NAME_REQUIRED);
                return;
            }
            PlayerData player = playerRepository.findByName(playerName.trim()).orElse(null);
            if (player == null) {
                ctx.status(404).result(MSG_PLAYER_NOT_FOUND + playerName);
                return;
            }
            List<BadgeDto> earned = badgeRepository.getBadges(player.uuid()).stream()
                    .map(BadgeDto::from)
                    .collect(Collectors.toList());
            ctx.json(Map.of(
                    KEY_PLAYER_NAME, player.username() != null ? player.username() : playerName,
                    "earnedCount", earned.size(),
                    "totalPossible", com.noahblclarkson.autotune.model.BadgeType.values().length,
                    "badges", earned
            ));
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
                            KEY_TYPE, e.type().name(),
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
                            KEY_TYPE, e.type().name(),
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
            if (debtGdpRatio < DEBT_GDP_MIN) {
                debtGdpLabel = "N/A";
            } else if (debtGdpRatio < DEBT_GDP_CAUTION) {
                debtGdpLabel = String.format("%.2fx", debtGdpRatio);
            } else if (debtGdpRatio < DEBT_GDP_DANGER) {
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
                Map<String, Object> entry = Map.ofEntries(
                        Map.entry("id", item.id()),
                        Map.entry("item", item.material().name()),
                        Map.entry(KEY_MATERIAL, item.material().name()),
                        Map.entry("displayName", item.getDisplayNameOrMaterial()),
                        Map.entry("avgBuyPrice", marketEngine.getBuyPrice(item).doubleValue()),
                        Map.entry("avgSellPrice", marketEngine.getSellPrice(item).doubleValue()),
                        Map.entry(KEY_PCT_CHANGE, pctChange),
                        Map.entry("change24h", pctChange),
                        Map.entry("changePercent24h", pctChange)
                );
                volatilities.add(entry);
                undersells.add(entry);
            }
            // Aggregate economy volatility (std dev of all items' 24h price changes)
            double avgVolatility = 0.0;
            if (!volatilities.isEmpty()) {
                double sum = volatilities.stream().mapToDouble(v -> (Double) v.get(KEY_PCT_CHANGE)).sum();
                double mean = sum / volatilities.size();
                double variance = volatilities.stream()
                        .mapToDouble(v -> {
                            double d = ((Double) v.get(KEY_PCT_CHANGE)) / 100.0 - mean;
                            return d * d;
                        })
                        .sum() / volatilities.size();
                avgVolatility = Math.sqrt(variance);
            }

            volatilities.sort((a, b) -> {
                double av = (Double) a.get(KEY_PCT_CHANGE);
                double bv = (Double) b.get(KEY_PCT_CHANGE);
                return Double.compare(Math.abs(bv), Math.abs(av)); // most volatile first
            });
            undersells.sort((a, b) -> {
                double av = (Double) a.get(KEY_PCT_CHANGE);
                double bv = (Double) b.get(KEY_PCT_CHANGE);
                return Double.compare(av, bv); // most negative first (undersold)
            });

            Map<String, Object> response = new HashMap<>();
            // Compute health score (0-100) from raw metrics — mirrors web/src/app/economy/page.tsx
            double volScore = avgVolatility < 0.05 ? 40.0 : avgVolatility < 0.10 ? 32.0
                    : avgVolatility < 0.15 ? 24.0 : avgVolatility < 0.25 ? 12.0 : 4.0;
            double d2gScore = 0.0;
            if (debtGdpRatio > 0 && debtGdpRatio < 3.0) d2gScore = 30.0;
            else if (debtGdpRatio >= 3.0 && debtGdpRatio < 8.0) d2gScore = 20.0;
            else if (debtGdpRatio >= 8.0 && debtGdpRatio < 15.0) d2gScore = 8.0;
            else if (debtGdpRatio >= 15.0) d2gScore = 0.0;
            double balScore = Math.min(30.0, (buyPct / 100.0) * 30.0);
            int healthScore = (int) Math.round(volScore + d2gScore + balScore);

            response.put("frozen", frozen);
            response.put("serverName", plugin.getServer().getName());
            response.put("healthScore", healthScore);
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
            response.put("avgVolatility", avgVolatility);
            response.put("topVolatileItems", volatilities.stream().limit(5).collect(Collectors.toList()));
            response.put("topUndersoldItems", undersells.stream().limit(5).collect(Collectors.toList()));
            response.put("topVolatile", volatilities.stream().limit(5).collect(Collectors.toList()));
            response.put("topUndersold", undersells.stream().limit(5).collect(Collectors.toList()));
            response.put("timestamp", System.currentTimeMillis());
            ctx.json(response);
        });

        // GET /api/admin/audit — recent admin audit log entries
        app.get("/api/admin/audit", ctx -> {
            int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(20);
            int effectiveLimit = Math.min(Math.max(1, limit), 200);
            var entries = auditService.getRecent(effectiveLimit);
            List<Map<String, Object>> result = entries.stream().map(entry -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", entry.id());
                m.put("timestamp", entry.timestamp().toString());
                m.put("adminUuid", entry.adminUuid() != null ? entry.adminUuid().toString() : null);
                m.put("adminName", entry.adminName());
                m.put("actionType", entry.actionType().name());
                m.put("target", entry.target());
                m.put("oldValue", entry.oldValue());
                m.put("newValue", entry.newValue());
                m.put("details", entry.details());
                m.put("summary", entry.toSummary());
                return m;
            }).collect(Collectors.toList());
            ctx.json(Map.of("entries", result, "count", result.size()));
        });

        // GET /api/admin/auction-audit — auction integrity and liquidity signals
        app.get("/api/admin/auction-audit", ctx -> {
            int days = ctx.queryParamAsClass("days", Integer.class).getOrDefault(7);
            int cappedDays = Math.min(Math.max(1, days), 30);
            var statusCounts = auctionRepository.countOrdersByStatus();
            var churn = auctionRepository.findOrderChurn(cappedDays);
            var materialHealth = auctionRepository.findMaterialBookHealth(50);
            long selfTradeFills = auctionRepository.countSelfTradeFills(cappedDays);

            Map<String, Object> statusDto = new LinkedHashMap<>();
            for (AuctionOrder.OrderStatus status : AuctionOrder.OrderStatus.values()) {
                statusDto.put(status.name().toLowerCase(Locale.ROOT), statusCounts.getOrDefault(status, 0L));
            }

            List<Map<String, Object>> thinBooks = materialHealth.stream()
                    .filter(AuctionRepository.MaterialBookHealth::isThinBook)
                    .limit(10)
                    .map(this::toMaterialBookHealthDto)
                    .collect(Collectors.toList());
            List<Map<String, Object>> largeSellWalls = materialHealth.stream()
                    .filter(AuctionRepository.MaterialBookHealth::hasLargeSellWall)
                    .limit(10)
                    .map(this::toMaterialBookHealthDto)
                    .collect(Collectors.toList());

            List<String> warnings = new ArrayList<>();
            if (selfTradeFills > 0) {
                warnings.add("Self-trade fills detected — review matching engine behavior and player activity.");
            }
            if (churn.cancellationRate() >= 0.35 && churn.totalOrders() >= 10) {
                warnings.add("High cancellation churn — watch for spoofing or players testing thin books.");
            }
            if (!thinBooks.isEmpty()) {
                warnings.add("Thin auction books found — listed prices may be easy to manipulate.");
            }
            if (!largeSellWalls.isEmpty()) {
                warnings.add("Large sell walls found — check for whale inventory dumps or price suppression.");
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("days", cappedDays);
            response.put("statusCounts", statusDto);
            response.put("churn", Map.of(
                    "totalOrders", churn.totalOrders(),
                    "activeOrders", churn.activeOrders(),
                    "filledOrders", churn.filledOrders(),
                    "cancelledOrders", churn.cancelledOrders(),
                    "expiredOrders", churn.expiredOrders(),
                    "reclaimedOrders", churn.reclaimedOrders(),
                    "cancellationRate", churn.cancellationRate(),
                    "fillRate", churn.fillRate(),
                    "expirationRate", churn.expirationRate()
            ));
            response.put("selfTradeFills", selfTradeFills);
            response.put("thinBooks", thinBooks);
            response.put("largeSellWalls", largeSellWalls);
            response.put("warnings", warnings);
            ctx.json(response);
        });

        // GET /api/admin/config — current config values vs defaults vs recommended ranges
        app.get("/api/admin/config", ctx -> {
            AutoTuneConfig cfg = configManager.getConfig();
            AutoTuneConfig.EconomyConfig econ = cfg.economy();
            AutoTuneConfig.SpreadConfig spd = econ.spread();
            AutoTuneConfig.LoanConfig loan = cfg.loans();

            Map<String, Object> response = new HashMap<>();

            // Spread section
            response.put("spread", Map.of(
                "baseSpread", Map.of(
                    KEY_CURRENT, spd.baseSpread(),
                    KEY_DEFAULT, 0.20,
                    KEY_RANGE_MIN, 0.15,
                    KEY_RANGE_MAX, 0.30,
                    KEY_UNIT, "decimal",
                    KEY_LABEL, "Base Spread"
                ),
                "volumeImpact", Map.of(
                    KEY_CURRENT, spd.volumeImpact(),
                    KEY_DEFAULT, 0.80,
                    KEY_RANGE_MIN, 0.50,
                    KEY_RANGE_MAX, 1.00,
                    KEY_UNIT, "decimal",
                    KEY_LABEL, "Volume Impact"
                ),
                "playerImpact", Map.of(
                    KEY_CURRENT, spd.playerImpact(),
                    KEY_DEFAULT, 0.60,
                    KEY_RANGE_MIN, 0.50,
                    KEY_RANGE_MAX, 1.00,
                    KEY_UNIT, "decimal",
                    KEY_LABEL, "Player Impact"
                )
            ));

            // Loan section
            response.put("loans", Map.of(
                "baseInterestRate", Map.of(
                    KEY_CURRENT, loan.baseInterestRate(),
                    KEY_DEFAULT, 0.05,
                    KEY_RANGE_MIN, 0.03,
                    KEY_RANGE_MAX, 0.10,
                    KEY_UNIT, "percent",
                    KEY_LABEL, "Base Interest Rate"
                ),
                "debtGdpTier3Ratio", Map.of(
                    KEY_CURRENT, loan.debtGdpTier3Ratio(),
                    KEY_DEFAULT, 15.0,
                    KEY_RANGE_MIN, 12.0,
                    KEY_RANGE_MAX, 15.0,
                    KEY_UNIT, "ratio",
                    KEY_LABEL, "Circuit Breaker Threshold"
                ),
                "postDefaultCooldownHours", Map.of(
                    KEY_CURRENT, loan.postDefaultCooldownHours(),
                    KEY_DEFAULT, 168,
                    KEY_RANGE_MIN, 72,
                    KEY_RANGE_MAX, 336,
                    KEY_UNIT, "hours",
                    KEY_LABEL, "Post-Default Cooldown"
                ),
                "counterCyclical", loan.counterCyclical(),
                "singleLoanGdpCap", loan.singleLoanGdpCap()
            ));

            // Economy section
            response.put("economy", Map.of(
                "tradeWindowDays", Map.of(
                    KEY_CURRENT, econ.tradeWindowDays(),
                    KEY_DEFAULT, 7,
                    KEY_RANGE_MIN, 5,
                    KEY_RANGE_MAX, 14,
                    KEY_UNIT, "days",
                    KEY_LABEL, "Trade Window"
                ),
                "maxPriceChangePercent", Map.of(
                    KEY_CURRENT, econ.maxPriceChangePercent(),
                    KEY_DEFAULT, 1.5,
                    KEY_RANGE_MIN, 1.0,
                    KEY_RANGE_MAX, 2.0,
                    KEY_UNIT, "percent",
                    KEY_LABEL, "Max Price Change"
                ),
                "minBuyQuantity", Map.of(
                    KEY_CURRENT, econ.minBuyQuantity(),
                    KEY_DEFAULT, 1,
                    KEY_RANGE_MIN, 1,
                    KEY_RANGE_MAX, 5,
                    KEY_UNIT, "items",
                    KEY_LABEL, "Min Buy Quantity"
                ),
                "minSellQuantity", Map.of(
                    KEY_CURRENT, econ.minSellQuantity(),
                    KEY_DEFAULT, 1,
                    KEY_RANGE_MIN, 1,
                    KEY_RANGE_MAX, 5,
                    KEY_UNIT, "items",
                    KEY_LABEL, "Min Sell Quantity"
                )
            ));

            // Market digest
            AutoTuneConfig.MarketDigestConfig md = cfg.marketDigest();
            response.put("marketDigest", Map.of(
                "enabled", md.enabled(),
                "interval", md.interval(),
                "includeTopMovers", md.includeTopMovers(),
                "includeHealthStats", md.includeHealthStats(),
                "includeActiveEvents", md.includeActiveEvents(),
                "includeLoanStats", md.includeLoanStats()
            ));

            ctx.json(response);
        });

        // ── Price alerts ───────────────────────────────────────────────────
        // GET /api/alerts/{playerName} — list all alerts for a player
        app.get("/api/alerts/{playerName}", ctx -> {
            String playerName = ctx.pathParam(KEY_PLAYER_NAME);
            if (playerName == null || playerName.isBlank()) {
                ctx.status(400).json(Map.of(KEY_ERROR, MSG_PLAYER_NAME_REQUIRED));
                return;
            }
            PlayerData player = playerRepository.findByName(playerName.trim()).orElse(null);
            if (player == null) {
                ctx.status(404).json(Map.of(KEY_ERROR, MSG_PLAYER_NOT_FOUND + playerName));
                return;
            }
            List<PriceAlert> alerts = priceAlertManager.getPlayerAlerts(player.uuid());
            List<AlertDto> dtos = alerts.stream()
                    .map(this::toAlertDto)
                    .collect(Collectors.toList());
            ctx.json(dtos);
        });

        // POST /api/alerts — create a new alert
        // Body: { KEY_PLAYER_NAME: "...", KEY_ITEM_ID: 123, "alertType": "ABOVE|BELOW", "targetPrice": 250.00 }
        app.post("/api/alerts", ctx -> {
            CreateAlertRequest req;
            try {
                req = gson.fromJson(ctx.body(), CreateAlertRequest.class);
            } catch (Exception e) {
                ctx.status(400).json(Map.of(KEY_ERROR, "Invalid request body"));
                return;
            }
            if (req.playerName == null || req.playerName.isBlank()) {
                ctx.status(400).json(Map.of(KEY_ERROR, MSG_PLAYER_NAME_REQUIRED));
                return;
            }
            if (req.itemId <= 0) {
                ctx.status(400).json(Map.of(KEY_ERROR, "itemId must be a positive integer"));
                return;
            }
            if (req.targetPrice == null || req.targetPrice <= 0) {
                ctx.status(400).json(Map.of(KEY_ERROR, "targetPrice must be a positive number"));
                return;
            }
            PriceAlert.AlertType alertType;
            try {
                alertType = PriceAlert.AlertType.valueOf(req.alertType.toUpperCase(Locale.ROOT));
            } catch (Exception e) {
                ctx.status(400).json(Map.of(KEY_ERROR, "alertType must be ABOVE or BELOW"));
                return;
            }
            // Resolve player UUID
            PlayerData player = playerRepository.findByName(req.playerName.trim()).orElse(null);
            UUID playerUuid;
            if (player != null) {
                playerUuid = player.uuid();
            } else {
                // Fall back to Bukkit lookup
                var offline = server.getOfflinePlayer(req.playerName);
                if (offline == null || !offline.hasPlayedBefore()) {
                    ctx.status(404).json(Map.of(KEY_ERROR, MSG_PLAYER_NOT_FOUND + req.playerName));
                    return;
                }
                playerUuid = offline.getUniqueId();
            }
            // Validate item exists
            if (itemRepository.findById(req.itemId).isEmpty()) {
                ctx.status(404).json(Map.of(KEY_ERROR, MSG_ITEM_NOT_FOUND + req.itemId));
                return;
            }
            var result = priceAlertManager.createAlert(
                    playerUuid, req.itemId, alertType, BigDecimal.valueOf(req.targetPrice));
            if (!result.success()) {
                ctx.status(400).json(Map.of(KEY_ERROR, result.errorMessage()));
                return;
            }
            ctx.status(201).json(toAlertDto(result.alert()));
        });

        // DELETE /api/alerts/{alertId}?playerName=X — remove an alert
        app.delete("/api/alerts/{alertId}", ctx -> {
            String alertId = ctx.pathParam("alertId");
            String playerName = ctx.queryParam(KEY_PLAYER_NAME);
            if (playerName == null || playerName.isBlank()) {
                ctx.status(400).json(Map.of(KEY_ERROR, MSG_PLAYER_NAME_QUERY_REQUIRED));
                return;
            }
            PlayerData player = playerRepository.findByName(playerName.trim()).orElse(null);
            if (player == null) {
                ctx.status(404).json(Map.of(KEY_ERROR, MSG_PLAYER_NOT_FOUND + playerName));
                return;
            }
            boolean removed = priceAlertManager.removeAlert(alertId, player.uuid(), false);
            if (!removed) {
                ctx.status(404).json(Map.of(KEY_ERROR, MSG_ALERT_NOT_FOUND_OR_NOT_OWNED));
                return;
            }
            ctx.json(Map.of("success", true));
        });

        // PATCH /api/alerts/{alertId}/toggle?playerName=X — enable/disable an alert
        app.patch("/api/alerts/{alertId}/toggle", ctx -> {
            String alertId = ctx.pathParam("alertId");
            String playerName = ctx.queryParam(KEY_PLAYER_NAME);
            if (playerName == null || playerName.isBlank()) {
                ctx.status(400).json(Map.of(KEY_ERROR, MSG_PLAYER_NAME_QUERY_REQUIRED));
                return;
            }
            PlayerData player = playerRepository.findByName(playerName.trim()).orElse(null);
            if (player == null) {
                ctx.status(404).json(Map.of(KEY_ERROR, MSG_PLAYER_NOT_FOUND + playerName));
                return;
            }
            boolean toggled = priceAlertManager.toggleAlert(alertId, player.uuid(), false);
            if (!toggled) {
                ctx.status(404).json(Map.of(KEY_ERROR, MSG_ALERT_NOT_FOUND_OR_NOT_OWNED));
                return;
            }
            // Fetch updated alert
            var alerts = priceAlertManager.getPlayerAlerts(player.uuid());
            alerts.stream()
                    .filter(a -> a.id().equals(alertId))
                    .findFirst()
                    .ifPresentOrElse(
                            a -> ctx.json(toAlertDto(a)),
                            () -> ctx.status(404).json(Map.of(KEY_ERROR, "Alert not found after toggle"))
                    );
        });

        // PATCH /api/alerts/{alertId}/rearm?playerName=X — rearm a triggered alert
        app.patch("/api/alerts/{alertId}/rearm", ctx -> {
            String alertId = ctx.pathParam("alertId");
            String playerName = ctx.queryParam(KEY_PLAYER_NAME);
            if (playerName == null || playerName.isBlank()) {
                ctx.status(400).json(Map.of(KEY_ERROR, MSG_PLAYER_NAME_QUERY_REQUIRED));
                return;
            }
            PlayerData player = playerRepository.findByName(playerName.trim()).orElse(null);
            if (player == null) {
                ctx.status(404).json(Map.of(KEY_ERROR, MSG_PLAYER_NOT_FOUND + playerName));
                return;
            }
            boolean rearmed = priceAlertManager.rearmAlert(alertId, player.uuid());
            if (!rearmed) {
                ctx.status(400).json(Map.of(KEY_ERROR, "Alert not found, not triggered, or not owned by player"));
                return;
            }
            var alerts = priceAlertManager.getPlayerAlerts(player.uuid());
            alerts.stream()
                    .filter(a -> a.id().equals(alertId))
                    .findFirst()
                    .ifPresentOrElse(
                            a -> ctx.json(toAlertDto(a)),
                            () -> ctx.status(404).json(Map.of(KEY_ERROR, "Alert not found after rearm"))
                    );
        });


        // ---- Streaks API ----

        // GET /api/streaks/{playerName} — player streak data
        app.get("/api/streaks/{playerName}", ctx -> {
            String playerName = ctx.pathParam(KEY_PLAYER_NAME);
            if (playerName == null || playerName.isBlank()) {
                ctx.status(400).json(Map.of(KEY_ERROR, MSG_PLAYER_NAME_REQUIRED));
                return;
            }
            PlayerData player = playerRepository.findByName(playerName.trim()).orElse(null);
            if (player == null) {
                ctx.status(404).json(Map.of(KEY_ERROR, MSG_PLAYER_NOT_FOUND + playerName));
                return;
            }
            PlayerStreakService.StreakData streak = streakService.getStreakOrDefault(player.uuid());
            Map<String, Object> result = Map.of(
                    "currentStreak", streak.currentStreak(),
                    "bestStreak", streak.bestStreak(),
                    "lastTradeDate", streak.lastTradeDate() != null ? streak.lastTradeDate().toString() : ""
            );
            ctx.json(result);
        });

        // GET /api/streaks/leaderboard — server-wide streak leaderboard
        app.get("/api/streaks/leaderboard", ctx -> {
            // Delegated to streak service for consistency
            ctx.json(List.of()); // placeholder — leaderboard is async, served from /streak top in-game
        });

        // ---- Shop Favorites API ----

        // GET /api/shop/favorites/{playerName} — list player's favorited item IDs
        app.get("/api/shop/favorites/{playerName}", ctx -> {
            String playerName = ctx.pathParam(KEY_PLAYER_NAME);
            if (playerName == null || playerName.isBlank()) {
                ctx.status(400).json(Map.of(KEY_ERROR, MSG_PLAYER_NAME_REQUIRED));
                return;
            }
            PlayerData player = playerRepository.findByName(playerName.trim()).orElse(null);
            if (player == null) {
                ctx.status(404).json(Map.of(KEY_ERROR, MSG_PLAYER_NOT_FOUND + playerName));
                return;
            }
            Set<Integer> favoriteIds = shopFavoriteRepository.getFavoriteItemIds(player.uuid());
            List<Map<String, Object>> favorites = favoriteIds.stream()
                    .map(id -> itemRepository.findById(id))
                    .filter(Optional::isPresent)
                    .map(item -> {
                        ShopItem si = item.get();
                        return Map.<String, Object>of(
                                "id", si.id(),
                                KEY_MATERIAL, si.material().name(),
                                "displayName", si.getDisplayNameOrMaterial(),
                                "section", si.section() != null ? si.section() : ""
                        );
                    })
                    .toList();
            ctx.json(Map.of(KEY_PLAYER_NAME, playerName, "favorites", favorites, "count", favorites.size()));
        });

        // POST /api/shop/favorites/{playerName}/{itemId} — add item to favorites
        app.post("/api/shop/favorites/{playerName}/{itemId}", ctx -> {
            String playerName = ctx.pathParam(KEY_PLAYER_NAME);
            int itemId = Integer.parseInt(ctx.pathParam(KEY_ITEM_ID));
            if (playerName == null || playerName.isBlank()) {
                ctx.status(400).json(Map.of(KEY_ERROR, MSG_PLAYER_NAME_REQUIRED));
                return;
            }
            PlayerData player = playerRepository.findByName(playerName.trim()).orElse(null);
            if (player == null) {
                ctx.status(404).json(Map.of(KEY_ERROR, MSG_PLAYER_NOT_FOUND + playerName));
                return;
            }
            if (itemRepository.findById(itemId).isEmpty()) {
                ctx.status(404).json(Map.of(KEY_ERROR, MSG_ITEM_NOT_FOUND + itemId));
                return;
            }
            shopFavoriteRepository.addFavorite(player.uuid(), itemId);
            ctx.status(201).json(Map.of(KEY_PLAYER_NAME, playerName, KEY_ITEM_ID, itemId, "favorited", true));
        });

        // DELETE /api/shop/favorites/{playerName}/{itemId} — remove item from favorites
        app.delete("/api/shop/favorites/{playerName}/{itemId}", ctx -> {
            String playerName = ctx.pathParam(KEY_PLAYER_NAME);
            int itemId = Integer.parseInt(ctx.pathParam(KEY_ITEM_ID));
            if (playerName == null || playerName.isBlank()) {
                ctx.status(400).json(Map.of(KEY_ERROR, MSG_PLAYER_NAME_REQUIRED));
                return;
            }
            PlayerData player = playerRepository.findByName(playerName.trim()).orElse(null);
            if (player == null) {
                ctx.status(404).json(Map.of(KEY_ERROR, MSG_PLAYER_NOT_FOUND + playerName));
                return;
            }
            shopFavoriteRepository.removeFavorite(player.uuid(), itemId);
            ctx.status(200).json(Map.of(KEY_PLAYER_NAME, playerName, KEY_ITEM_ID, itemId, "favorited", false));
        });

        // PATCH /api/shop/favorites/{playerName}/{itemId} — toggle favorite
        app.patch("/api/shop/favorites/{playerName}/{itemId}", ctx -> {
            String playerName = ctx.pathParam(KEY_PLAYER_NAME);
            int itemId = Integer.parseInt(ctx.pathParam(KEY_ITEM_ID));
            if (playerName == null || playerName.isBlank()) {
                ctx.status(400).json(Map.of(KEY_ERROR, MSG_PLAYER_NAME_REQUIRED));
                return;
            }
            PlayerData player = playerRepository.findByName(playerName.trim()).orElse(null);
            if (player == null) {
                ctx.status(404).json(Map.of(KEY_ERROR, MSG_PLAYER_NOT_FOUND + playerName));
                return;
            }
            if (itemRepository.findById(itemId).isEmpty()) {
                ctx.status(404).json(Map.of(KEY_ERROR, MSG_ITEM_NOT_FOUND + itemId));
                return;
            }
            boolean isFav = shopFavoriteRepository.isFavorite(player.uuid(), itemId);
            if (isFav) {
                shopFavoriteRepository.removeFavorite(player.uuid(), itemId);
            } else {
                shopFavoriteRepository.addFavorite(player.uuid(), itemId);
            }
            ctx.json(Map.of(KEY_PLAYER_NAME, playerName, KEY_ITEM_ID, itemId, "favorited", !isFav));
        });

        app.exception(Exception.class, (e, ctx) -> {
            plugin.getLogger().log(Level.WARNING, "Web API error: " + e.getMessage());
            ctx.status(500).json(Map.of(KEY_ERROR, "Internal server error"));
        });
    }

    private void registerWebSocket() {
        app.ws("/ws/market", ws -> {
            ws.onConnect(ctx -> {
                // Keep connections alive for 10 minutes of inactivity (dashboard polls every 30 s).
                ctx.session.setIdleTimeout(Duration.ofMinutes(10));
                wsClients.add(ctx);
                ctx.send(gson.toJson(Map.of(KEY_TYPE, "connected", "message", "Connected to market feed")));
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
                KEY_TYPE, "price_update",
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
                .orElse(MSG_UNKNOWN);
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

    public record CircuitEventDto(
            long id,
            String previousTier,
            String newTier,
            double debtGdpRatio,
            double gdp,
            double totalDebt,
            double interestMultiplier,
            boolean adminInitiated,
            String details,
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

    public record AlertDto(
            String id,
            String playerUuid,
            int itemId,
            String itemName,
            String alertType,
            double targetPrice,
            double currentPrice,
            boolean enabled,
            boolean triggered,
            long createdAt,
            Long triggeredAt
    ) {
    }

    public record CreateAlertRequest(
            String playerName,
            int itemId,
            String alertType,
            Double targetPrice
    ) {
    }

    private AlertDto toAlertDto(PriceAlert alert) {
        String itemName = itemRepository.findById(alert.itemId())
                .map(ShopItem::getDisplayNameOrMaterial)
                .orElse("#" + alert.itemId());
        double currentPrice = 0.0;
        try {
            currentPrice = marketEngine.getCurrentPrice(alert.itemId()).doubleValue();
        } catch (Exception e) {
            plugin.getLogger().fine("Price not yet available for alert item " + alert.itemId() + ": " + e.getMessage());
        }
        return new AlertDto(
                alert.id(),
                alert.playerUuid().toString(),
                alert.itemId(),
                itemName,
                alert.alertType().name(),
                alert.targetPrice().doubleValue(),
                currentPrice,
                alert.enabled(),
                alert.isTriggered(),
                alert.createdAt().toEpochMilli(),
                alert.triggeredAt() != null ? alert.triggeredAt().toEpochMilli() : null
        );
    }

    private Map<String, Object> toMaterialBookHealthDto(AuctionRepository.MaterialBookHealth health) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("material", health.material());
        m.put("bidCount", health.bidCount());
        m.put("askCount", health.askCount());
        m.put("bidQuantity", health.bidQuantity());
        m.put("askQuantity", health.askQuantity());
        m.put("bestBid", health.bestBid() != null ? health.bestBid().doubleValue() : null);
        m.put("bestAsk", health.bestAsk() != null ? health.bestAsk().doubleValue() : null);
        m.put("largestSellQuantity", health.largestSellQuantity());
        m.put("thinBook", health.isThinBook());
        m.put("largeSellWall", health.hasLargeSellWall());
        return m;
    }

    private Map<String, Object> toAuctionOrderDto(AuctionOrder order) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", order.id().toString());
        m.put("playerUuid", order.playerUuid().toString());
        m.put("material", order.material());
        m.put("price", order.price().doubleValue());
        m.put("originalQuantity", order.originalQuantity());
        m.put("remainingQuantity", order.remainingQuantity());
        m.put("filledQuantity", order.filledQuantity());
        m.put("side", order.side().name());
        m.put("status", order.status().name());
        m.put("createdAt", order.createdAt().toEpochMilli());
        m.put("expiresAt", order.expiresAt().toEpochMilli());
        m.put("isActive", order.isActive());
        return m;
    }
}
