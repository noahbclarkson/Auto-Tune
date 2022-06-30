package unprotesting.com.github.data;

import java.io.Serializable;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

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

  public static enum TransactionType {
    BUY,
    SELL
  }
  
}
