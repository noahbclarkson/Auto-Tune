package com.noahblclarkson.autotune.economy;

import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.config.AutoTuneConfig;
import com.noahblclarkson.autotune.config.AutoTuneConfig.LoanConfig;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.database.DatabaseManager;
import com.noahblclarkson.autotune.database.EconomySnapshotRepository;
import com.noahblclarkson.autotune.database.LoanRepository;
import com.noahblclarkson.autotune.database.PlayerRepository;
import com.noahblclarkson.autotune.manager.TreasuryService;
import com.noahblclarkson.autotune.model.EconomySnapshot;
import com.noahblclarkson.autotune.model.Loan;
import com.noahblclarkson.autotune.model.PlayerData;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LoanManager loan computation logic.
 * Uses inline mock setup to avoid test framework issues.
 */
class LoanManagerTest {

    private final UUID playerUuid = UUID.randomUUID();
    private final String playerName = "TestPlayer";

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    /** Creates a LoanConfig with all defaults except any overrides specified. */
    private static LoanConfig loanCfg(
            Boolean enabled, Double baseInterestRate, Boolean creditScoreModifier,
            Double maxLoanMultiplier, Integer minCreditScore,
            Integer minTermDays, Integer maxTermDays,
            Double termPremiumPerDay, Integer compoundIntervalHours,
            Double inflationRateImpact
    ) {
        LoanConfig d = LoanConfig.defaults();
        return new LoanConfig(
                enabled != null ? enabled : d.enabled(),
                baseInterestRate != null ? baseInterestRate : d.baseInterestRate(),
                creditScoreModifier != null ? creditScoreModifier : d.creditScoreModifier(),
                maxLoanMultiplier != null ? maxLoanMultiplier : d.maxLoanMultiplier(),
                minCreditScore != null ? minCreditScore : d.minCreditScore(),
                d.defaultDurationDays(),
                minTermDays != null ? minTermDays : d.minTermDays(),
                maxTermDays != null ? maxTermDays : d.maxTermDays(),
                termPremiumPerDay != null ? termPremiumPerDay : d.termPremiumPerDay(),
                compoundIntervalHours != null ? compoundIntervalHours : d.compoundIntervalHours(),
                d.overdueCheckIntervalHours(), d.warningBeforeDueHours(),
                d.earlyRepaymentBonusMultiplier(),
                inflationRateImpact != null ? inflationRateImpact : d.inflationRateImpact(),
                d.defaultPenalty(), d.debtGdpCircuitBreakerRatio()
        );
    }

    private AutoTuneConfig fullConfig(LoanConfig loanCfg) {
        return new AutoTuneConfig(
                new AutoTuneConfig.StorageConfig(
                        AutoTuneConfig.StorageConfig.StorageType.SQLITE,
                        "localhost", 3306, "test.db", "user", "pass",
                        AutoTuneConfig.StorageConfig.PoolConfig.defaults()
                ),
                AutoTuneConfig.WebConfig.defaults(),
                AutoTuneConfig.EconomyConfig.defaults(),
                loanCfg,
                AutoTuneConfig.GuiConfig.defaults(),
                AutoTuneConfig.PriceReporterConfig.defaults(),
                AutoTuneConfig.AutosellConfig.defaults(),
                AutoTuneConfig.DebugConfig.defaults(),
                AutoTuneConfig.EnchantmentConfig.defaults(),
                AutoTuneConfig.CleanupConfig.defaults(),
                AutoTuneConfig.TaxConfig.defaults(),
                false
        );
    }

    private LoanManager makeLoanManager(LoanConfig loanCfg, Economy economy,
                                       LoanRepository loanRepo, PlayerRepository playerRepo,
                                       EconomySnapshotRepository snapRepo,
                                       PlayerData playerData
    ) {
        ConfigManager cfgMgr = mock(ConfigManager.class);
        when(cfgMgr.getConfig()).thenReturn(fullConfig(loanCfg));
        LoanManager lm = new LoanManager(mock(AutoTune.class), cfgMgr, economy,
                mock(DatabaseManager.class), loanRepo, playerRepo, snapRepo,
                mock(TreasuryService.class));
        if (playerData != null) {
            when(playerRepo.findByUuid(playerUuid)).thenReturn(Optional.of(playerData));
        }
        return lm;
    }

    // -----------------------------------------------------------------
    // Interest Rate Calculation Tests
    // -----------------------------------------------------------------

    @Nested
    @DisplayName("getInterestRate")
    class InterestRateTests {

        private LoanManager newLoanManager(LoanConfig cfg, PlayerData pd) {
            return makeLoanManager(cfg, new FakeEconomy(),
                    mock(LoanRepository.class), mock(PlayerRepository.class),
                    mock(EconomySnapshotRepository.class), pd);
        }

        @Test
        @DisplayName("returns base rate when modifier is disabled")
        void baseRateNoModifier() {
            LoanConfig cfg = loanCfg(false, 0.05, false, null, null, null, null, 0.0, null, null);
            PlayerData pd = new PlayerData(playerUuid, playerName, 700,
                    BigDecimal.valueOf(5000), BigDecimal.ZERO, BigDecimal.ZERO, 0,
                    Instant.now(), Instant.now());
            LoanManager lm = newLoanManager(cfg, pd);
            assertEquals(0, lm.getInterestRate(playerUuid, 30).compareTo(BigDecimal.valueOf(0.05)));
        }

        @Test
        @DisplayName("increases rate for poor credit score")
        void poorCreditScore() {
            LoanConfig cfg = loanCfg(true, 0.10, true, null, 500, null, null, 0.0, null, null);
            PlayerData pd = new PlayerData(playerUuid, playerName, 300,
                    BigDecimal.valueOf(5000), BigDecimal.ZERO, BigDecimal.ZERO, 0,
                    Instant.now(), Instant.now());
            LoanManager lm = newLoanManager(cfg, pd);
            assertTrue(lm.getInterestRate(playerUuid, 30).doubleValue() > 0.10);
        }

        @Test
        @DisplayName("decreases rate for excellent credit score")
        void excellentCreditScore() {
            LoanConfig cfg = loanCfg(true, 0.10, true, null, 500, null, null, 0.0, null, null);
            PlayerData pd = new PlayerData(playerUuid, playerName, 900,
                    BigDecimal.valueOf(5000), BigDecimal.ZERO, BigDecimal.ZERO, 0,
                    Instant.now(), Instant.now());
            LoanManager lm = newLoanManager(cfg, pd);
            assertTrue(lm.getInterestRate(playerUuid, 30).doubleValue() < 0.10);
        }

        @Test
        @DisplayName("adds term premium for loans beyond minimum term")
        void termPremium() {
            LoanConfig cfg = loanCfg(true, 0.05, false, null, null, 30, 365, 0.01, null, null);
            PlayerData pd = new PlayerData(playerUuid, playerName, 700,
                    BigDecimal.valueOf(5000), BigDecimal.ZERO, BigDecimal.ZERO, 0,
                    Instant.now(), Instant.now());
            LoanManager lm = newLoanManager(cfg, pd);
            BigDecimal r30 = lm.getInterestRate(playerUuid, 30);
            BigDecimal r60 = lm.getInterestRate(playerUuid, 60);
            assertEquals(r30.doubleValue() + 0.30, r60.doubleValue(), 0.001);
        }

        @Test
        @DisplayName("clamps term beyond maxTermDays")
        void termClampedToMax() {
            LoanConfig cfg = loanCfg(true, 0.05, false, null, null, 30, 365, 0.01, null, null);
            LoanManager lm = newLoanManager(cfg, null);
            // maxTermDays = 365, minTermDays = 30, termPremiumPerDay = 0.01
            // 500-day term: clamped to 365 → rate = 0.05 + (365-30)*0.01 = 3.40
            // 200-day term: no clamp → rate = 0.05 + (200-30)*0.01 = 1.75
            // Use compareTo for BigDecimal equality (ignores scale differences).
            assertEquals(0, new BigDecimal("3.40").compareTo(lm.getInterestRate(playerUuid, 500)));
            assertEquals(0, new BigDecimal("1.75").compareTo(lm.getInterestRate(playerUuid, 200)));
        }
    }

    // -------------------------------------------------------------------------
    // Loan Request Validation Tests
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("requestLoan validation")
    class RequestLoanValidation {

        private Player mockPlayer() {
            Player p = mock(Player.class);
            when(p.getUniqueId()).thenReturn(playerUuid);
            when(p.getName()).thenReturn(playerName);
            return p;
        }

        private LoanManager newLoanManager(LoanConfig cfg, Loan existingLoan) {
            PlayerRepository pr = mock(PlayerRepository.class);
            LoanRepository lr = mock(LoanRepository.class);
            PlayerData pd = new PlayerData(playerUuid, playerName, 700,
                    BigDecimal.valueOf(5000), BigDecimal.ZERO, BigDecimal.ZERO, 0,
                    Instant.now(), Instant.now());
            when(pr.getOrCreate(playerUuid, playerName)).thenReturn(pd);
            when(pr.findByUuid(playerUuid)).thenReturn(Optional.of(pd));
            when(lr.findActiveByPlayer(playerUuid)).thenReturn(
                    Optional.ofNullable(existingLoan));
            return makeLoanManager(cfg, new FakeEconomy(),
                    lr, pr, mock(EconomySnapshotRepository.class), pd);
        }

        @Test
        @DisplayName("rejects when loans are disabled")
        void disabled() {
            LoanConfig cfg = loanCfg(false, null, null, null, null, null, null, null, null, null);
            LoanManager lm = newLoanManager(cfg, null);
            LoanManager.LoanResult r = lm.requestLoan(mockPlayer(), BigDecimal.valueOf(100), 30);
            assertFalse(r.success());
            assertEquals("Loans are disabled", r.errorMessage());
        }

        @Test
        @DisplayName("rejects zero and negative amounts")
        void nonPositiveAmount() {
            LoanManager lm = newLoanManager(LoanConfig.defaults(), null);
            LoanManager.LoanResult neg = lm.requestLoan(mockPlayer(), BigDecimal.valueOf(-50), 30);
            assertFalse(neg.success());
            assertEquals("Loan amount must be positive", neg.errorMessage());
            LoanManager.LoanResult zero = lm.requestLoan(mockPlayer(), BigDecimal.ZERO, 30);
            assertFalse(zero.success());
            assertEquals("Loan amount must be positive", zero.errorMessage());
        }

        @Test
        @DisplayName("rejects when player already has an active loan")
        void alreadyHasLoan() {
            Loan existingLoan = Loan.builder()
                    .playerUuid(playerUuid)
                    .principal(BigDecimal.valueOf(200))
                    .currentBalance(BigDecimal.valueOf(180))
                    .interestRate(BigDecimal.valueOf(0.10))
                    .dueDate(Instant.now().plusSeconds(86400 * 30))
                    .build();
            LoanManager lm = newLoanManager(LoanConfig.defaults(), existingLoan);
            LoanManager.LoanResult r = lm.requestLoan(mockPlayer(), BigDecimal.valueOf(100), 30);
            assertFalse(r.success());
            assertTrue(r.errorMessage().contains("active loan"));
        }

        @Test
        @DisplayName("rejects when credit score is below minimum")
        void insufficientCredit() {
            PlayerRepository pr = mock(PlayerRepository.class);
            PlayerData lowCredit = new PlayerData(playerUuid, playerName, 400,
                    BigDecimal.valueOf(5000), BigDecimal.ZERO, BigDecimal.ZERO, 0,
                    Instant.now(), Instant.now());
            when(pr.getOrCreate(playerUuid, playerName)).thenReturn(lowCredit);
            when(pr.findByUuid(playerUuid)).thenReturn(Optional.of(lowCredit));

            LoanConfig cfg = loanCfg(true, null, null, null, 600, null, null, null, null, null);
            LoanManager lm = makeLoanManager(cfg, new FakeEconomy(),
                    mock(LoanRepository.class), pr, mock(EconomySnapshotRepository.class), lowCredit);
            LoanManager.LoanResult r = lm.requestLoan(mockPlayer(), BigDecimal.valueOf(100), 30);
            assertFalse(r.success());
            assertTrue(r.errorMessage().contains("Credit score too low"));
        }

        @Test
        @DisplayName("rejects loan exceeding max")
        void exceedsMaxLoan() {
            LoanManager lm = newLoanManager(LoanConfig.defaults(), null);
            LoanManager.LoanResult r = lm.requestLoan(mockPlayer(), BigDecimal.valueOf(15000), 30);
            assertFalse(r.success());
            assertTrue(r.errorMessage().contains("maximum loan"));
        }

        @Test
        @DisplayName("succeeds for valid loan request")
        void success() {
            LoanManager lm = newLoanManager(LoanConfig.defaults(), null);
            LoanManager.LoanResult r = lm.requestLoan(mockPlayer(), BigDecimal.valueOf(500), 30);
            assertTrue(r.success(), "Valid request should succeed: " + r.errorMessage());
            assertNotNull(r.loan());
            assertEquals(0, r.loan().principal().compareTo(BigDecimal.valueOf(500)));
            assertEquals(Loan.LoanStatus.ACTIVE, r.loan().status());
        }
    }

    // -------------------------------------------------------------------------
    // Max Loan Amount Tests
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("getMaxLoanAmount")
    class MaxLoanAmountTests {

        @Test
        @DisplayName("returns floor of 100 for unknown players")
        void unknownPlayerFloor() {
            PlayerRepository pr = mock(PlayerRepository.class);
            when(pr.findByUuid(playerUuid)).thenReturn(Optional.empty());
            LoanManager lm = makeLoanManager(LoanConfig.defaults(), new FakeEconomy(),
                    mock(LoanRepository.class), pr, mock(EconomySnapshotRepository.class), null);
            assertEquals(0, lm.getMaxLoanAmount(playerUuid).compareTo(BigDecimal.valueOf(100)));
        }

        @Test
        @DisplayName("calculates max from totalTraded × maxLoanMultiplier")
        void fromTradingHistory() {
            PlayerData pd = new PlayerData(playerUuid, playerName, 700,
                    BigDecimal.valueOf(1000), BigDecimal.ZERO, BigDecimal.ZERO, 0,
                    Instant.now(), Instant.now());
            LoanConfig cfg = loanCfg(true, null, null, 3.0, null, null, null, null, null, null);
            LoanManager lm = makeLoanManager(cfg, new FakeEconomy(),
                    mock(LoanRepository.class), mock(PlayerRepository.class),
                    mock(EconomySnapshotRepository.class), pd);
            // 1000 * 3.0 = 3000
            assertEquals(0, lm.getMaxLoanAmount(playerUuid).compareTo(BigDecimal.valueOf(3000)));
        }

        @Test
        @DisplayName("enforces floor of 100 when calculation is below floor")
        void floorPreventsSmallLoan() {
            PlayerData pd = new PlayerData(playerUuid, playerName, 700,
                    BigDecimal.valueOf(100), BigDecimal.ZERO, BigDecimal.ZERO, 0,
                    Instant.now(), Instant.now());
            LoanConfig cfg = loanCfg(true, null, null, 0.5, null, null, null, null, null, null);
            LoanManager lm = makeLoanManager(cfg, new FakeEconomy(),
                    mock(LoanRepository.class), mock(PlayerRepository.class),
                    mock(EconomySnapshotRepository.class), pd);
            // 100 * 0.5 = 50, but floor is 100
            assertEquals(0, lm.getMaxLoanAmount(playerUuid).compareTo(BigDecimal.valueOf(100)));
        }
    }

    // -------------------------------------------------------------------------
    // Amortization Tests
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("getAmortizationPayment")
    class AmortizationTests {

        @Test
        @DisplayName("returns balance divided by periods when rate is zero")
        void zeroRate() {
            LoanConfig cfg = loanCfg(true, null, null, null, null, null, null, null, 24, null);
            PlayerData pd = new PlayerData(playerUuid, playerName, 700,
                    BigDecimal.valueOf(5000), BigDecimal.ZERO, BigDecimal.ZERO, 0,
                    Instant.now(), Instant.now());
            LoanManager lm = makeLoanManager(cfg, new FakeEconomy(),
                    mock(LoanRepository.class), mock(PlayerRepository.class),
                    mock(EconomySnapshotRepository.class), pd);
            Loan loan = Loan.builder()
                    .playerUuid(playerUuid)
                    .principal(BigDecimal.valueOf(300))
                    .currentBalance(BigDecimal.valueOf(300))
                    .interestRate(BigDecimal.ZERO)
                    .dueDate(Instant.now().plusSeconds(86400 * 30))
                    .build();
            // 300 / 30 periods = 10
            assertEquals(0, lm.getAmortizationPayment(loan).compareTo(BigDecimal.valueOf(10.00)));
        }

        @Test
        @DisplayName("returns positive payment for positive rate and balance")
        void positiveRate() {
            LoanConfig cfg = loanCfg(true, null, null, null, null, null, null, null, 24, null);
            PlayerData pd = new PlayerData(playerUuid, playerName, 700,
                    BigDecimal.valueOf(5000), BigDecimal.ZERO, BigDecimal.ZERO, 0,
                    Instant.now(), Instant.now());
            LoanManager lm = makeLoanManager(cfg, new FakeEconomy(),
                    mock(LoanRepository.class), mock(PlayerRepository.class),
                    mock(EconomySnapshotRepository.class), pd);
            Loan loan = Loan.builder()
                    .playerUuid(playerUuid)
                    .principal(BigDecimal.valueOf(1000))
                    .currentBalance(BigDecimal.valueOf(1000))
                    .interestRate(BigDecimal.valueOf(0.05))
                    .dueDate(Instant.now().plusSeconds(86400 * 24))
                    .build();
            assertTrue(lm.getAmortizationPayment(loan).doubleValue() > 0);
        }
    }

    // -------------------------------------------------------------------------
    // Loan Model Tests — pure logic, no mocks needed
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Loan model")
    class LoanModelTests {

        @Test
        @DisplayName("makePayment: fully pays off when payment >= balance")
        void overpayment() {
            Loan loan = Loan.builder()
                    .playerUuid(playerUuid)
                    .principal(BigDecimal.valueOf(1000))
                    .currentBalance(BigDecimal.valueOf(300))
                    .interestRate(BigDecimal.valueOf(0.10))
                    .dueDate(Instant.now().plusSeconds(86400 * 30))
                    .build();
            Loan paid = loan.makePayment(BigDecimal.valueOf(500));
            assertEquals(Loan.LoanStatus.PAID, paid.status());
            assertEquals(0, paid.currentBalance().compareTo(BigDecimal.ZERO));
        }

        @Test
        @DisplayName("makePayment: partial payment reduces balance, keeps ACTIVE status")
        void partialPayment() {
            Loan loan = Loan.builder()
                    .playerUuid(playerUuid)
                    .principal(BigDecimal.valueOf(1000))
                    .currentBalance(BigDecimal.valueOf(500))
                    .interestRate(BigDecimal.valueOf(0.10))
                    .dueDate(Instant.now().plusSeconds(86400 * 30))
                    .build();
            Loan paid = loan.makePayment(BigDecimal.valueOf(200));
            assertEquals(Loan.LoanStatus.ACTIVE, paid.status());
            assertEquals(0, paid.currentBalance().compareTo(BigDecimal.valueOf(300)));
        }

        @Test
        @DisplayName("applyInterest: compounds balance")
        void applyInterest() {
            Loan loan = Loan.builder()
                    .playerUuid(playerUuid)
                    .principal(BigDecimal.valueOf(1000))
                    .currentBalance(BigDecimal.valueOf(1000))
                    .interestRate(BigDecimal.valueOf(0.10))
                    .dueDate(Instant.now().plusSeconds(86400 * 30))
                    .lastInterestAt(Instant.now().minusSeconds(86400 * 2))
                    .build();
            Loan withInterest = loan.applyInterest(BigDecimal.valueOf(0.10));
            assertEquals(0, withInterest.currentBalance().compareTo(BigDecimal.valueOf(1100)));
            assertEquals(Loan.LoanStatus.ACTIVE, withInterest.status());
        }

        @Test
        @DisplayName("markDefaulted: changes status without touching balance")
        void markDefaulted() {
            Loan loan = Loan.builder()
                    .playerUuid(playerUuid)
                    .principal(BigDecimal.valueOf(1000))
                    .currentBalance(BigDecimal.valueOf(1200))
                    .interestRate(BigDecimal.valueOf(0.10))
                    .dueDate(Instant.now().minusSeconds(86400))
                    .build();
            Loan defaulted = loan.markDefaulted();
            assertEquals(Loan.LoanStatus.DEFAULTED, defaulted.status());
            assertEquals(0, defaulted.currentBalance().compareTo(BigDecimal.valueOf(1200)));
        }

        @Test
        @DisplayName("isOverdue: true when past due date and ACTIVE")
        void isOverdue() {
            Loan overdueLoan = Loan.builder()
                    .playerUuid(playerUuid)
                    .principal(BigDecimal.valueOf(1000))
                    .currentBalance(BigDecimal.valueOf(1000))
                    .interestRate(BigDecimal.valueOf(0.10))
                    .dueDate(Instant.now().minusSeconds(3600))
                    .status(Loan.LoanStatus.ACTIVE)
                    .build();
            Loan activeLoan = Loan.builder()
                    .playerUuid(playerUuid)
                    .principal(BigDecimal.valueOf(1000))
                    .currentBalance(BigDecimal.valueOf(1000))
                    .interestRate(BigDecimal.valueOf(0.10))
                    .dueDate(Instant.now().plusSeconds(86400 * 30))
                    .status(Loan.LoanStatus.ACTIVE)
                    .build();
            assertTrue(overdueLoan.isOverdue());
            assertFalse(activeLoan.isOverdue());
        }
    }
}
