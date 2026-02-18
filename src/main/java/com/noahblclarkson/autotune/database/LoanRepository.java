package com.noahblclarkson.autotune.database;

import com.noahblclarkson.autotune.model.Loan;
import com.noahblclarkson.autotune.model.Loan.LoanStatus;
import org.jdbi.v3.core.Jdbi;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class LoanRepository {

    private final Jdbi jdbi;

    public LoanRepository(DatabaseManager databaseManager) {
        this.jdbi = databaseManager.getJdbi();
    }

    public Optional<Loan> findById(UUID id) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                                SELECT id, player_uuid, principal, current_balance, interest_rate,
                                       created_at, due_date, last_interest_at, status
                                FROM at_loans WHERE id = :id
                                """)
                        .bind("id", id.toString())
                        .map((rs, ctx) -> Loan.builder()
                                .id(UUID.fromString(rs.getString("id")))
                                .playerUuid(UUID.fromString(rs.getString("player_uuid")))
                                .principal(rs.getBigDecimal("principal"))
                                .currentBalance(rs.getBigDecimal("current_balance"))
                                .interestRate(rs.getBigDecimal("interest_rate"))
                                .createdAt(rs.getTimestamp("created_at").toInstant())
                                .dueDate(rs.getTimestamp("due_date").toInstant())
                                .lastInterestAt(rs.getTimestamp("last_interest_at").toInstant())
                                .status(LoanStatus.valueOf(rs.getString("status")))
                                .build())
                        .findFirst());
    }

    public Optional<Loan> findActiveByPlayer(UUID playerUuid) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                                SELECT id, player_uuid, principal, current_balance, interest_rate,
                                       created_at, due_date, last_interest_at, status
                                FROM at_loans WHERE player_uuid = :playerUuid AND status = 'ACTIVE'
                                """)
                        .bind("playerUuid", playerUuid.toString())
                        .map((rs, ctx) -> Loan.builder()
                                .id(UUID.fromString(rs.getString("id")))
                                .playerUuid(UUID.fromString(rs.getString("player_uuid")))
                                .principal(rs.getBigDecimal("principal"))
                                .currentBalance(rs.getBigDecimal("current_balance"))
                                .interestRate(rs.getBigDecimal("interest_rate"))
                                .createdAt(rs.getTimestamp("created_at").toInstant())
                                .dueDate(rs.getTimestamp("due_date").toInstant())
                                .lastInterestAt(rs.getTimestamp("last_interest_at").toInstant())
                                .status(LoanStatus.valueOf(rs.getString("status")))
                                .build())
                        .findFirst());
    }

    public List<Loan> findByPlayer(UUID playerUuid) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                                SELECT id, player_uuid, principal, current_balance, interest_rate,
                                       created_at, due_date, last_interest_at, status
                                FROM at_loans WHERE player_uuid = :playerUuid
                                ORDER BY created_at DESC
                                """)
                        .bind("playerUuid", playerUuid.toString())
                        .map((rs, ctx) -> Loan.builder()
                                .id(UUID.fromString(rs.getString("id")))
                                .playerUuid(UUID.fromString(rs.getString("player_uuid")))
                                .principal(rs.getBigDecimal("principal"))
                                .currentBalance(rs.getBigDecimal("current_balance"))
                                .interestRate(rs.getBigDecimal("interest_rate"))
                                .createdAt(rs.getTimestamp("created_at").toInstant())
                                .dueDate(rs.getTimestamp("due_date").toInstant())
                                .lastInterestAt(rs.getTimestamp("last_interest_at").toInstant())
                                .status(LoanStatus.valueOf(rs.getString("status")))
                                .build())
                        .list());
    }

    public List<Loan> findAllActive() {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                                SELECT id, player_uuid, principal, current_balance, interest_rate,
                                       created_at, due_date, last_interest_at, status
                                FROM at_loans WHERE status = 'ACTIVE'
                                """)
                        .map((rs, ctx) -> Loan.builder()
                                .id(UUID.fromString(rs.getString("id")))
                                .playerUuid(UUID.fromString(rs.getString("player_uuid")))
                                .principal(rs.getBigDecimal("principal"))
                                .currentBalance(rs.getBigDecimal("current_balance"))
                                .interestRate(rs.getBigDecimal("interest_rate"))
                                .createdAt(rs.getTimestamp("created_at").toInstant())
                                .dueDate(rs.getTimestamp("due_date").toInstant())
                                .lastInterestAt(rs.getTimestamp("last_interest_at").toInstant())
                                .status(LoanStatus.valueOf(rs.getString("status")))
                                .build())
                        .list());
    }

    public List<Loan> findOverdueLoans() {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                                SELECT id, player_uuid, principal, current_balance, interest_rate,
                                       created_at, due_date, last_interest_at, status
                                FROM at_loans WHERE status = 'ACTIVE' AND due_date < :now
                                """)
                        .bind("now", Timestamp.from(Instant.now()))
                        .map((rs, ctx) -> Loan.builder()
                                .id(UUID.fromString(rs.getString("id")))
                                .playerUuid(UUID.fromString(rs.getString("player_uuid")))
                                .principal(rs.getBigDecimal("principal"))
                                .currentBalance(rs.getBigDecimal("current_balance"))
                                .interestRate(rs.getBigDecimal("interest_rate"))
                                .createdAt(rs.getTimestamp("created_at").toInstant())
                                .dueDate(rs.getTimestamp("due_date").toInstant())
                                .lastInterestAt(rs.getTimestamp("last_interest_at").toInstant())
                                .status(LoanStatus.valueOf(rs.getString("status")))
                                .build())
                        .list());
    }

    public void insert(@NotNull Loan loan) {
        jdbi.useHandle(handle ->
                handle.createUpdate("""
                                INSERT INTO at_loans (id, player_uuid, principal, current_balance,
                                                      interest_rate, created_at, due_date,
                                                      last_interest_at, status)
                                VALUES (:id, :playerUuid, :principal, :currentBalance,
                                        :interestRate, :createdAt, :dueDate,
                                        :lastInterestAt, :status)
                                """)
                        .bind("id", loan.id().toString())
                        .bind("playerUuid", loan.playerUuid().toString())
                        .bind("principal", loan.principal())
                        .bind("currentBalance", loan.currentBalance())
                        .bind("interestRate", loan.interestRate())
                        .bind("createdAt", Timestamp.from(loan.createdAt()))
                        .bind("dueDate", Timestamp.from(loan.dueDate()))
                        .bind("lastInterestAt", Timestamp.from(loan.lastInterestAt()))
                        .bind("status", loan.status().name())
                        .execute());
    }

    public void update(@NotNull Loan loan) {
        jdbi.useHandle(handle ->
                handle.createUpdate("""
                                UPDATE at_loans SET
                                    current_balance = :currentBalance,
                                    last_interest_at = :lastInterestAt,
                                    status = :status
                                WHERE id = :id
                                """)
                        .bind("id", loan.id().toString())
                        .bind("currentBalance", loan.currentBalance())
                        .bind("lastInterestAt", Timestamp.from(loan.lastInterestAt()))
                        .bind("status", loan.status().name())
                        .execute());
    }

    public void updateBalance(UUID loanId, BigDecimal newBalance) {
        jdbi.useHandle(handle ->
                handle.createUpdate("""
                                UPDATE at_loans SET current_balance = :balance WHERE id = :id
                                """)
                        .bind("id", loanId.toString())
                        .bind("balance", newBalance)
                        .execute());
    }

    public void updateStatus(UUID loanId, LoanStatus status) {
        jdbi.useHandle(handle ->
                handle.createUpdate("""
                                UPDATE at_loans SET status = :status WHERE id = :id
                                """)
                        .bind("id", loanId.toString())
                        .bind("status", status.name())
                        .execute());
    }

    public int countActive() {
        return jdbi.withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM at_loans WHERE status = 'ACTIVE'")
                        .mapTo(Integer.class)
                        .one());
    }

    public int countOverdue() {
        return jdbi.withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM at_loans WHERE status = 'ACTIVE' AND due_date < :now")
                        .bind("now", Timestamp.from(Instant.now()))
                        .mapTo(Integer.class)
                        .one());
    }

    public int countDefaultedByPlayer(UUID playerUuid) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                                SELECT COUNT(*) FROM at_loans
                                WHERE player_uuid = :playerUuid AND status = 'DEFAULTED'
                                """)
                        .bind("playerUuid", playerUuid.toString())
                        .mapTo(Integer.class)
                        .one());
    }
}
