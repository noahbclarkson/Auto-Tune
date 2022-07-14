package unprotesting.com.github.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.UUID;

/**
 * The class that represents a Loan.
 */
@AllArgsConstructor
@Data
@Builder
public class Transaction implements Serializable {

    private static final long serialVersionUID = -7234917640151336711L;

    private double price;
    private int amount;
    private UUID player;
    private String item;
    private TransactionType position;

    /**
     * The type/position of the transaction.
     */
    public static enum TransactionType {
        BUY,
        SELL
    }

}
