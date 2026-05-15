package com.noahblclarkson.autotune.economy;

import com.noahblclarkson.autotune.economy.EconomyManager.TransactionResult;
import com.noahblclarkson.autotune.model.Transaction.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionResultTest {

    @Test
    void successCarriesTransactionDetails() {
        TransactionResult result = TransactionResult.success(
                TransactionType.SELL,
                4,
                BigDecimal.valueOf(123.45)
        );

        assertTrue(result.success());
        assertNull(result.errorMessage());
        assertEquals(TransactionType.SELL, result.type());
        assertEquals(4, result.amount());
        assertEquals(0, result.totalPrice().compareTo(BigDecimal.valueOf(123.45)));
    }

    @Test
    void insufficientFundsIncludesRequiredTotal() {
        TransactionResult result = TransactionResult.insufficientFunds(BigDecimal.valueOf(99.99));

        assertFalse(result.success());
        assertEquals("Insufficient funds. Required: 99.99", result.errorMessage());
        assertEquals(0, result.totalPrice().compareTo(BigDecimal.valueOf(99.99)));
    }

    @Test
    void belowMinimumReportsQuantityConstraint() {
        TransactionResult result = TransactionResult.belowMinimum(TransactionType.BUY, 10, 0, 3);

        assertFalse(result.success());
        assertEquals("Transaction too small. Minimum quantity: 10", result.errorMessage());
        assertEquals(TransactionType.BUY, result.type());
        assertEquals(3, result.amount());
    }

    @Test
    void belowMinimumReportsValueConstraintWhenConfigured() {
        TransactionResult result = TransactionResult.belowMinimum(TransactionType.SELL, 1, 5.50, 3);

        assertFalse(result.success());
        assertEquals("Transaction too small. Minimum value: $5.50", result.errorMessage());
        assertEquals(TransactionType.SELL, result.type());
        assertEquals(3, result.amount());
    }
}
