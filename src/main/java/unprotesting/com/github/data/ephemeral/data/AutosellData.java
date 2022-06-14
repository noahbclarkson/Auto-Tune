package unprotesting.com.github.data.ephemeral.data;

import java.util.concurrent.ConcurrentHashMap;

import lombok.Getter;

public class AutosellData {

  @Getter
  private ConcurrentHashMap<String, Double> data;

  /**
   * Initializes the autosell data.
   */
  public AutosellData() {
    data = new ConcurrentHashMap<String, Double>();
  }

  /**
   * Add a new item to the autosell data.
   * @param uuid The player's uuid.
   * @param amount The amount of the item.
   */
  public void add(String uuid, Double amount) {

    // If the item already exists, add the amount to the existing amount.
    // Otherwise, add the item to the data.
    if (data.containsKey(uuid)) {
      data.put(uuid, data.get(uuid) + amount);
      return;
    } else {
      data.put(uuid, amount);
    }

  }

}
