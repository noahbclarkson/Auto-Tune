package unprotesting.com.github.data.ephemeral;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import lombok.Data;
import lombok.SneakyThrows;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import unprotesting.com.github.Main;
import unprotesting.com.github.commands.objects.Section;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.config.Messages;
import unprotesting.com.github.data.ephemeral.data.EconomyInfoData;
import unprotesting.com.github.data.ephemeral.data.EnchantmentData;
import unprotesting.com.github.data.ephemeral.data.GdpData;
import unprotesting.com.github.data.ephemeral.data.ItemData;
import unprotesting.com.github.data.ephemeral.data.LoanData;
import unprotesting.com.github.data.ephemeral.data.MaxBuySellData;
import unprotesting.com.github.data.ephemeral.data.TransactionData;
import unprotesting.com.github.data.ephemeral.other.PlayerSaleData;
import unprotesting.com.github.data.ephemeral.other.Sale;
import unprotesting.com.github.data.ephemeral.other.Sale.SalePositionType;
import unprotesting.com.github.data.persistent.TimePeriod;
import unprotesting.com.github.data.persistent.timeperiods.EnchantmentsTimePeriod;
import unprotesting.com.github.data.persistent.timeperiods.GdpTimePeriod;
import unprotesting.com.github.data.persistent.timeperiods.ItemTimePeriod;
import unprotesting.com.github.data.persistent.timeperiods.LoanTimePeriod;
import unprotesting.com.github.data.persistent.timeperiods.TransactionsTimePeriod;
import unprotesting.com.github.util.UtilFunctions;

@Data
public class LocalDataCache {

  private HashMap<String, ItemData> items;
  private HashMap<String, EnchantmentData> enchantments;
  private List<LoanData> loans;
  private List<LoanData> newLoans;
  private List<TransactionData> transactions;
  private List<TransactionData> newTransactions;
  private HashMap<String, PlayerSaleData> playerSales;
  private List<Section> sections;
  private HashMap<String, MaxBuySellData> maxPurchases;
  private HashMap<String, Double> percentageChanges;
  private GdpData gdpData;
  private EconomyInfoData economyInfo;
  private int size;

  /**
   * Initializes the local data cache.
   */
  public LocalDataCache() {
    this.items = new HashMap<String, ItemData>();
    this.enchantments = new HashMap<String, EnchantmentData>();
    this.loans = new ArrayList<LoanData>();
    this.newLoans = new ArrayList<LoanData>();
    this.transactions = new ArrayList<TransactionData>();
    this.newTransactions = new ArrayList<TransactionData>();
    this.playerSales = new HashMap<String, PlayerSaleData>();
    this.sections = new ArrayList<Section>();
    this.maxPurchases = new HashMap<String, MaxBuySellData>();
    this.percentageChanges = new HashMap<String, Double>();
    this.size = Main.getInstance().getDatabase().getMap().size();
    init();
  }

  /**
   * Add a new sale to related maps.
   * @param uuid The player's uuid.
   * @param item The item being sold.
   * @param price The price of the item.
   * @param amount The amount of the item being sold.
   * @param position The sale position type of the sale.
   */
  public void addSale(UUID uuid, String item, double price, int amount, SalePositionType position) {

    OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
    PlayerSaleData playerSaleData = getPlayerSaleData(player);
    playerSaleData.addSale(item, amount, position);
    playerSales.put(player.getUniqueId().toString(), playerSaleData);
    addSale(new TransactionData(uuid.toString(), item, amount, price, position));

  }

  /**
   * Add an item sale to map.
   * @param transaction The transaction to add to the map.
   */
  private void addSale(TransactionData transaction) {

    ItemData itemData = items.get(transaction.getItem());
    EnchantmentData enchantmentData = enchantments.get(transaction.getItem());

    switch (transaction.getPosition()) {

      case BUY:

        itemData.increaseBuys(transaction.getAmount());
        items.put(transaction.getItem(), itemData);
        break;

      case SELL:

        gdpData.increaseLoss((transaction.getAmount() * getItemPrice(transaction.getItem(), false))
            - (transaction.getAmount() * transaction.getPrice()));

        itemData.increaseSells(transaction.getAmount());
        items.put(transaction.getItem(), itemData);
        break;

      case EBUY:

        enchantmentData.increaseBuys(transaction.getAmount());
        enchantments.put(transaction.getItem(), enchantmentData);
        break;

      case ESELL:

        gdpData.increaseLoss((transaction.getAmount() * getItemPrice(transaction.getItem(), false))
            - (transaction.getAmount() * transaction.getPrice()));

        enchantmentData.increaseSells(transaction.getAmount());
        enchantments.put(transaction.getItem(), enchantmentData);
        break;

      default:
        return;
    }

    transactions.add(transaction);
    newTransactions.add(transaction);
    gdpData.increaseGdp(transaction.getAmount() * transaction.getPrice() / 2);

  }

  /**
   * Add new loan to ephemeral cache.
   * @param value The value of the loan.
   * @param interestRate The interest rate of the loan.
   * @param player The player who is borrowing.
   */
  public void addLoan(double value, double interestRate, OfflinePlayer player) {

    LoanData data = new LoanData(value, interestRate, player.getUniqueId().toString());
    loans.add(data);
    newLoans.add(data);

    // If the player is online then return.
    if (!player.isOnline()) {
      return;
    }

    Player onlinePlayer = player.getPlayer();
    DecimalFormat df = UtilFunctions.getDf();
    double minutelyUpdateRate = Config.getConfig().getInterestRateUpdateRate() / 1200;

    TagResolver resolver = TagResolver.resolver(

        Placeholder.unparsed("total", df.format(value)),
        Placeholder.unparsed("interest", df.format(interestRate)),
        Placeholder.unparsed("update-rate", df.format(minutelyUpdateRate))

    );

    Component message = Main.getInstance().getMm().deserialize(
          Messages.getMessages().getLoanSuccess(), resolver);
    
    onlinePlayer.sendMessage(message);

    Collections.sort(loans);

  }

  /**
   * Get the price of an item.
   * @param item The item to get the price of.
   * @param sell Whether the item is being sold or bought.
   * @return The price of the item.
   */
  public double getItemPrice(String item, boolean sell) {

    ItemData data = items.get(item);

    // If the itemData is null then return.
    if (data == null) {
      return 0;
    }

    // If the item is being bought then return the price.
    if (!sell) {
      return data.getPrice();
    }

    Double spd = Config.getConfig().getSellPriceDifference();

    // If a custom sell price difference is set then set the spd to the custom value.
    if (Main.getInstance().getDataFiles().getShops().getConfigurationSection(
        "shops").getConfigurationSection(item).contains("sell-difference")) {

      spd = Main.getInstance().getDataFiles().getShops().getConfigurationSection(
        "shops").getConfigurationSection(item).getDouble("sell-difference");

    }

    return (data.getPrice() - data.getPrice() * spd * 0.01);

  }

  /**
   * Get the price of an enchantment.
   * @param enchantment The enchantment to get the price of.
   * @param sell Whether the enchantment is being sold or bought.
   * @return The price of the enchantment.
   */
  public double getEnchantmentPrice(String enchantment, boolean sell) {

    EnchantmentData data = enchantments.get(enchantment);

    // If the enchantmentData is null then return.
    if (data == null) {
      return 0;
    }

    // If the enchantment is being bought then return the price.
    if (!sell) {
      return data.getPrice();
    }

    Double spd = Config.getConfig().getSellPriceDifference();

    // If a custom sell price difference is set then set the spd to the custom value.
    if (Main.getInstance().getDataFiles().getEnchantments().getConfigurationSection(
        "enchantments").getConfigurationSection(enchantment).contains("sell-difference")) {

      spd = Main.getInstance().getDataFiles().getEnchantments().getConfigurationSection(
        "enchantments").getConfigurationSection(enchantment).getDouble("sell-difference");

    }

    return (data.getPrice() - data.getPrice() * spd * 0.01);

  }

  /**
   * Get the enchantment ratio of an enchantment.
   * @param enchantment The enchantment to get the ratio of.
   * @return The enchantment ratio.
   */
  public double getEnchantmentRatio(String enchantment) {

    EnchantmentData data = enchantments.get(enchantment);

    // If the enchantmentData is null then return.
    if (data == null) {
      return 0;
    }

    return data.getRatio();

  }

  /**
   * Get price of an item with an enchantment.
   * @param enchantment The enchantment to get the price of.
   * @param itemPrice The price of the item.
   * @param sell Whether the item is being sold or bought.
   * @return The price of the item with the enchantment.
   */
  public double getOverallEnchantmentPrice(String enchantment, double itemPrice, boolean sell) {

    double price = getEnchantmentPrice(enchantment, sell);
    double ratio = getEnchantmentRatio(enchantment);
    double total = price + (itemPrice * ratio);
    total -= (total * Config.getConfig().getEnchantmentLimiter() * 0.01);
    return itemPrice + total;

  }

  /**
   * Get the amount of buys or sells left for an item.
   * @param item The item to get the amount of buys or sells left for.
   * @param player The player to get the amount of buys or sells left for.
   * @param isBuys Whether the amount of buys or sells left is being requested.
   * @return The amount of buys or sells left for the item.
   */
  public int getPurchasesLeft(String item, OfflinePlayer player, boolean isBuys) {

    int defaultMax = 99999;
    PlayerSaleData saleData = playerSales.getOrDefault(player.getUniqueId().toString(), null);
    MaxBuySellData maxData = maxPurchases.get(item);

    // If the saleData or maxData is null then return the default max.
    // If "disable-max-buys-sells" is true then return the default max.
    if (maxData == null || Config.getConfig().isDisableMaxBuysSells()) {
      return defaultMax;
    }

    if (saleData == null) {
      if (isBuys) {
        return maxData.getBuys();
      } else {
        return maxData.getSells();
      }
    }

    int maxBuys = maxData.getBuys();
    int maxSells = maxData.getSells();
    int buys = countSales(saleData.getBuys(), item);
    int sells = countSales(saleData.getSells(), item);

    // If the player is buying then return the maxBuys - buys.
    // If the player is selling then return the maxSells - sells.
    if (isBuys) {
      return maxBuys - buys;
    } else {
      return maxSells - sells;
    }

  }

  /**
   * Get the amount of sales for an item.
   * @param sales The sales to get the amount of sales for.
   * @return The amount of sales for the item.
   */
  private int countSales(List<Sale> sales, String item) {
    int amount = 0;
    for (Sale sale : sales) {
      if (!sale.getItem().equals(item)) {
        continue;
      }
      amount += sale.getAmount();
    }
    return amount;
  }

  /**
   * Get the percentage change string for an item.
   * @param item The item to get the percentage change string for.
   * @return The percentage change string for the item.
   */
  public String getChangeString(String item) {

    DecimalFormat df = new DecimalFormat(Config.getConfig().getNumberFormat());
    Double change = percentageChanges.get(item);

    if (change == null) {
      return (ChatColor.GRAY + "0.0%");
    }

    if (change < -0.005) {
      return (ChatColor.RED + df.format(change) + "%");
    }

    if (change > 0.005) {
      return (ChatColor.GREEN + df.format(change) + "%");
    } else {
      return (ChatColor.GRAY + "0.0%");
    }

  }

  /**
   * Update percentage changes for all items.
   */
  public void updatePercentageChanges() {

    // If the size is 1 or 2 then return.
    if (size < 2) {
      return;
    }

    int tpInDay = (int) Math.floor(1.0 / (Config.getConfig().getTimePeriod() / 1440.0));
    int base = size - tpInDay > 0 ? size - tpInDay : 0;
    TimePeriod timePeriodLatest = Main.getInstance().getDatabase().getMap().get((size - 1));
    TimePeriod timePeriodBase = Main.getInstance().getDatabase().getMap().get(base);
    ItemTimePeriod itemLatest = timePeriodLatest.getItemTP();
    ItemTimePeriod itemBase = timePeriodBase.getItemTP();
    EnchantmentsTimePeriod enchantmentLatest = timePeriodLatest.getEnchantmentsTP();
    EnchantmentsTimePeriod enchantmentBase = timePeriodBase.getEnchantmentsTP();


    // Loop through all items.
    for (String item : itemLatest.getItems()) {

      int latestPosition = Arrays.asList(itemLatest.getItems()).indexOf(item);
      int basePosition = Arrays.asList(itemBase.getItems()).indexOf(item);
      double latestPrice = itemLatest.getPrices()[latestPosition];
      double basePrice = itemBase.getPrices()[basePosition];
      double change = (latestPrice - basePrice) / basePrice * 100;
      percentageChanges.put(item, change);

    }

    // Loop through all enchantments.
    for (String enchantment : enchantmentLatest.getItems()) {

      int latestPosition = Arrays.asList(enchantmentLatest.getItems()).indexOf(enchantment);
      int basePosition = Arrays.asList(enchantmentBase.getItems()).indexOf(enchantment);
      double latestPrice = enchantmentLatest.getPrices()[latestPosition];
      double basePrice = enchantmentBase.getPrices()[basePosition];
      double change = (latestPrice - basePrice) / basePrice * 100;
      percentageChanges.put(enchantment, change);

    }

  }

  /**
   * Initialize cache from database and files.
   */
  private void init() {

    loadShopDataFromFile();
    loadShopDataFromData();
    loadEnchantmentDataFromFile();
    loadLoanDataFromData();
    loadTransactionDataFromData();
    loadSectionDataFromFile();
    loadGdpDataFromData();
    loadEconomyInfoDataFromFile();
    loadEconomyInfoDataFromData();
    updatePercentageChanges();

  }

  /**
   * Get the current cache for the PlayerSaleData object.
   * @param player The player to get the PlayerSaleData object for.
   * @return The PlayerSaleData object for the player.
   */
  private PlayerSaleData getPlayerSaleData(OfflinePlayer player) {
    return playerSales.getOrDefault(player.getUniqueId().toString(), new PlayerSaleData());
  }

  /**
   * Load the default shops and data from the files.
   */
  private void loadShopDataFromFile() {

    ConfigurationSection config = Main.getInstance().getDataFiles()
        .getShops().getConfigurationSection("shops");

    Set<String> set = config.getKeys(false);

    // Loop through keys in shops section of config file.
    for (String key : set) {

      ConfigurationSection section = config.getConfigurationSection(key);

      // If the material is not a valid material, skip it and throw an error.
      if (Material.matchMaterial(key) == null) {

        Main.getInstance().getLogger().severe("Invalid item in shops.yml: " + key);
        continue;

      }

      ItemData data = new ItemData(section.getDouble("price", 0.0));

      MaxBuySellData maxBuySellData = new MaxBuySellData(
          section.getInt("max-buy", 99999), section.getInt("max-sell", 99999));

      maxPurchases.put(key, maxBuySellData);
      items.put(key, data);

    }

  }

  private void loadShopDataFromData() {

    // If the size is less than 2 set the percentage changes to 0.
    if (size < 2) {

      for (String str : items.keySet()) {
        percentageChanges.put(str, 0.0);
      }

      for (String str : enchantments.keySet()) {
        percentageChanges.put(str, 0.0);
      }

    }

    // if the size is 0 return.
    if (size == 0) {
      return;
    }

    ItemTimePeriod itp = Main.getInstance().getDatabase().getMap().get(size - 1).getItemTP();

    EnchantmentsTimePeriod etp = Main.getInstance()
        .getDatabase().getMap().get(size - 1).getEnchantmentsTP();

    // Loop through all items.
    for (int i = 0; i < itp.getItems().length; i++) {

      String item = itp.getItems()[i];
      ItemData data = new ItemData(itp.getPrices()[i]);
      items.put(item, data);

    }

    // Loop through all enchantments.
    for (int i = 0; i < etp.getItems().length; i++) {

      String enchantment = etp.getItems()[i];
      EnchantmentData data = new EnchantmentData(etp.getPrices()[i], etp.getRatios()[i]);
      enchantments.put(enchantment, data);

    }

  }

  private void loadEnchantmentDataFromFile() {

    ConfigurationSection config = Main.getInstance().getDataFiles()
        .getEnchantments().getConfigurationSection("enchantments");

    Set<String> set = config.getKeys(false);

    // Loop through keys in enchantments section of config file.
    for (String key : set) {

      // If the enchantment is not a valid enchantment, skip it and throw an error.
      if (NamespacedKey.minecraft(key.toLowerCase()) == null) {
        Main.getInstance().getLogger().severe("Invalid enchantment in enchantments.yml: " + key);
        continue;
      }

      ConfigurationSection sec = config.getConfigurationSection(key);

      EnchantmentData data = new EnchantmentData(
          sec.getDouble("price", 0.0), sec.getDouble("ratio", 0.0));

      enchantments.put(key, data);

    }
  }

  private void loadLoanDataFromData() {

    // If the size is less than 1 return.
    if (size < 1) {
      return;
    }

    loans.clear();

    // Loop through all time periods.
    for (Integer pos : Main.getInstance().getDatabase().getMap().keySet()) {

      LoanTimePeriod ltp = Main.getInstance().getDatabase().getMap().get(pos).getLoanTP();

      // Loop through all loans.
      for (int i = 0; i < ltp.getValues().length; i++) {

        LoanData data = new LoanData(ltp.getValues()[i], ltp.getInterestRates()[i],
            ltp.getPlayers()[i], ltp.getTime()[i]);

        loans.add(data);

      }

    }

    Collections.sort(loans);

  }

  // Map of legacy position types to new position types.
  private static final Map<String, String> legacyPositionTypes = new HashMap<String, String>() {

    {

      put("BI", "BUY");
      put("SI", "SELL");
      put("BE", "EBUY");
      put("SE", "ESELL");

    }

  };

  @SneakyThrows
  private void loadTransactionDataFromData() {

    // If the size is less than 1 return.
    if (size < 1) {
      return;
    }

    transactions.clear();

    // Loop through all time periods.
    for (Integer pos : Main.getInstance().getDatabase().getMap().keySet()) {

      TransactionsTimePeriod ttp = Main.getInstance().getDatabase()
          .getMap().get(pos).getTransactionsTP();

      // Loop through all transactions.
      for (int i = 0; i < ttp.getPrices().length; i++) {

        SalePositionType position;

        try {
          position = SalePositionType.valueOf(ttp.getPositions()[i]);
        } catch (Exception e) {
          
          // If the position is not a valid position, check if it is a legacy position.
          if (legacyPositionTypes.containsKey(ttp.getPositions()[i])) {
            ttp.getPositions()[i] = legacyPositionTypes.get(ttp.getPositions()[i]);
            position = SalePositionType.valueOf(ttp.getPositions()[i]);
          } else {

            Main.getInstance().getLogger().severe(
                "Invalid position in transaction data: " + ttp.getPositions()[i]);

            continue;
          }
            

        }

        
        

        TransactionData data = new TransactionData(ttp.getPlayers()[i], ttp.getItems()[i],
            ttp.getAmounts()[i], ttp.getPrices()[i], position, ttp.getTime()[i]);

        transactions.add(data);

      }

    }

    Collections.sort(transactions);

  }

  private void loadSectionDataFromFile() {

    ConfigurationSection config = Main.getInstance().getDataFiles().getShops()
        .getConfigurationSection("sections");

    // Loop through all keys in config file and add section data to map.
    for (String key : config.getKeys(false)) {
      sections.add(new Section(config.getConfigurationSection(key), key));
    }

    config = Main.getInstance().getDataFiles().getEnchantments().getConfigurationSection("config");
    sections.add(new Section(config, "Enchantments"));

  }

  private void loadGdpDataFromData() {

    // If the size is less than 1 initialize the gdpData object.
    if (size < 1) {

      gdpData = new GdpData(0, 0, 0, 0, 0, 0);
      return;

    }

    GdpTimePeriod gtp = Main.getInstance().getDatabase().getMap().get(size - 1).getGdpTP();

    gdpData = new GdpData(gtp.getGdp(), gtp.getBalance(), gtp.getLoss(),
      gtp.getDebt(), gtp.getInflation(), gtp.getPlayerCount());

  }

  private void loadEconomyInfoDataFromFile() {
    economyInfo = new EconomyInfoData(Config.getConfig().getSellPriceDifferenceVariationStart());
  }

  private void loadEconomyInfoDataFromData() {

    // If the size is less than 1 return.
    if (size < 1) {
      return;
    }

    economyInfo = new EconomyInfoData(Main.getInstance().getDatabase()
      .getMap().get(size - 1).getEconomyInfoTP().getSellPriceDifference());

  }

}
