package unprotesting.com.github.data.ephemeral.data;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NonNull;
import unprotesting.com.github.data.ephemeral.other.Sale.SalePositionType;
import unprotesting.com.github.data.util.LocalDateTimeArrayUtilizer;

//  Transaction class for storing general transaction data

public class TransactionData extends LocalDateTimeArrayUtilizer
     implements Comparable<TransactionData> {

  @Getter
  private LocalDateTime date;
  @Getter
  private String item;
  @Getter
  private String player;
  @Getter
  private int amount;
  @Getter
  private double price;
  @Getter
  private SalePositionType position;

  /**
   * Initializes the transaction data.
   * @param uuid The UUID of the player.
   * @param item The item name.
   * @param amount The amount of the item.
   * @param price The price of the item.
   * @param position The TransactionPositionType of the transaction.
   */
  public TransactionData(@NonNull String uuid, String item, int amount,
       double price, SalePositionType position) {

    this.date = LocalDateTime.now();
    this.player = uuid;
    this.item = item;
    this.amount = amount;
    this.price = price;
    this.position = position;
  }

  /**
   * Initializes the transaction data using previous persisted data.
   * @param uuid The UUID of the player.
   * @param item The item name.
   * @param amount The amount of the item.
   * @param price The price of the item.
   * @param position The TransactionPositionType of the transaction.
   * @param date The time array of the transaction.
   */
  public TransactionData(@NonNull String uuid, String item, int amount, 
      double price, SalePositionType position, int[] date) {

    this.date = arrayToDate(date);
    this.player = uuid;
    this.item = item;
    this.amount = amount;
    this.price = price;
    this.position = position;

  }

  /**
   * Compares the transaction data.
   */
  @Override
  public int compareTo(TransactionData o) {
    return o.getDate().compareTo(getDate());
  }

}
