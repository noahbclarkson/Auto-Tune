package unprotesting.com.github.data;

import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.mapdb.BTreeMap;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;
import org.mapdb.DBMaker.Maker;
import org.mapdb.serializer.SerializerArray;
import org.mapdb.serializer.SerializerCompressionWrapper;

import unprotesting.com.github.Main;
import unprotesting.com.github.commands.objects.Section;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.config.Messages;
import unprotesting.com.github.data.objects.EconomyData;
import unprotesting.com.github.data.objects.Loan;
import unprotesting.com.github.data.objects.MaxBuySellData;
import unprotesting.com.github.data.objects.Shop;
import unprotesting.com.github.data.objects.Transaction;
import unprotesting.com.github.data.objects.Transaction.SalePositionType;
import unprotesting.com.github.economy.EconomyFunctions;
import unprotesting.com.github.util.UtilFunctions;

@Getter
public class Database {

  private DB db;
  private HTreeMap<String, Shop> shops;
  private HTreeMap<String, EconomyData> economyData;
  private BTreeMap<Long, Loan[]> loans;
  private BTreeMap<Long, Transaction[]> transactions;
  private List<Section> sections;
  @Setter
  private HashMap<String, MaxBuySellData> maxPurchases;
  private List<Transaction> recentTransactions = new ArrayList<>();

  public static final String[] ECONOMY_DATA_KEYS = {
      "GDP", "BALANCE", "DEBT", "LOSS", "INFLATION", "POPULATION", "SPD"};

  /**
   * Initializes the database.
   */
  public Database() {

    createDB(Config.getConfig().getDataLocation() + "data.db");

    this.shops = db.hashMap("shops")
        .keySerializer(new SerializerCompressionWrapper<String>(Serializer.STRING))
        .valueSerializer(new Shop.ShopSerializer())
        .createOrOpen();
    
    this.economyData = db.hashMap("economyData")
        .keySerializer(new SerializerCompressionWrapper<String>(Serializer.STRING))
        .valueSerializer(new EconomyData.EconomyDataSerializer())
        .createOrOpen();

    SerializerArray<Loan> loanSerializer = new SerializerArray<Loan>(new Loan.LoanSerializer());

    SerializerArray<Transaction> transactionSerializer = new SerializerArray<Transaction>(
        new Transaction.TransactionSerializer());

    this.loans = db.treeMap("loans")
        .keySerializer(Serializer.LONG)
        .valueSerializer(loanSerializer)
        .createOrOpen();

    this.transactions = db.treeMap("transactions")
        .keySerializer(Serializer.LONG)
        .valueSerializer(transactionSerializer)
        .createOrOpen();
      
    this.loans.descendingMap();
    this.transactions.descendingMap();
    this.sections = new ArrayList<Section>();
    this.maxPurchases = new HashMap<String, MaxBuySellData>();
    loadShopDefaults();
    
    for (String key : ECONOMY_DATA_KEYS) {
      if (!economyData.containsKey(key)) {

        economyData.put(key, new EconomyData());

        if (key.equals("SPD")) {

          // Use EconomyData.update() to update the economy data in the map
          // after the initial creation.
          economyData.get(key).update(30);

        }

      }
    }

    for (String key : economyData.keySet()) {
      Main.getInstance().getLogger().info(key + ": " + economyData.get(key).getValue());
    }

    loadSectionDataFromFile();
    updateEconomyInfo();

    for (String key : economyData.keySet()) {
      Main.getInstance().getLogger().info(key + ": " + economyData.get(key).getValue());
    }

  }


  /**
   * Build and create or link database to file.
   * @param location The location of the database.
   */
  private void createDB(String location) {

    Maker maker = DBMaker.fileDB(location);

    db = maker.checksumHeaderBypass()
        .fileMmapEnableIfSupported()
        .fileMmapPreclearDisable()
        .cleanerHackEnable()
        .allocateStartSize(10 * 1024 * 1024) // 25MB
        .allocateIncrement(5 * 1024 * 1024) // 5MB
        .closeOnJvmShutdown().make();

    db.getStore().fileLoad();

  }

  /**
   * Add a new sale to related maps.
   * @param transaction The transaction being added.
   */
  public void addSale(Transaction transaction) {

    Shop shop = shops.get(transaction.getItem());

    if (shop == null) {
      Main.getInstance().getLogger().warning("Shop not found for item: " + transaction.getItem());
      return;
    }

    switch (transaction.getPosition()) {
      case BUY:
        shop.addToBuyCount(transaction.getAmount());
        break;
      case SELL:
        shop.addToSellCount(transaction.getAmount());
        double loss = transaction.getAmount() * (shop.getPrice() - transaction.getPrice());
        economyData.get("LOSS").increase(loss);
        break;
      default:
        Main.getInstance().getLogger().warning(
            "Unknown transaction sale type: " + transaction.getType());
        break;
    }

    transactions.put(System.currentTimeMillis(), new Transaction[]{transaction});
    recentTransactions.add(transaction);
    economyData.get("GDP").increase(transaction.getPrice() * transaction.getAmount());

  }

  /**
   * Get a shop from the database.
   */
  public Shop getShop(String item) {
    return shops.get(item);
  }

  /**
   * Add new loan to ephemeral cache.
   * @param value The value of the loan.
   * @param player The player who is borrowing.
   */
  public void addLoan(double value, OfflinePlayer player) {

    Loan loan = new Loan(value, value, player.getUniqueId(), false);
    loans.put(System.currentTimeMillis(), new Loan[]{loan});

    // If the player is online then return.
    if (!player.isOnline()) {
      return;
    }

    Player onlinePlayer = player.getPlayer();
    DecimalFormat df = UtilFunctions.getDf();
    double interest = Config.getConfig().getInterestRate();
    double minutelyUpdateRate = Config.getConfig().getInterestRateUpdateRate() / 1200;

    TagResolver resolver = TagResolver.resolver(

        Placeholder.unparsed("total", df.format(value)),
        Placeholder.unparsed("interest", df.format(interest)),
        Placeholder.unparsed("update-rate", df.format(minutelyUpdateRate))

    );

    Component message = Main.getInstance().getMm().deserialize(
          Messages.getMessages().getLoanSuccess(), resolver);
    
    onlinePlayer.sendMessage(message);

  }

  /**
   * Load shop defaults.
   */
  public void loadShopDefaults() {

    ConfigurationSection config = Main.getInstance().getDataFiles()
        .getShops().getConfigurationSection("shops");

    for (String key : config.getKeys(false)) {

      key = key.toUpperCase();
      ConfigurationSection section = config.getConfigurationSection(key);

      if (Material.matchMaterial(key) == null) {

        Main.getInstance().getLogger().severe("Invalid item in shops.yml: " + key);
        continue;

      }

      if (section == null) {
          
        Main.getInstance().getLogger().severe("Invalid enchantment in enchantments.yml: " + key
            + " (missing section)");

        continue;
  
      }

      MaxBuySellData maxBuySellData = new MaxBuySellData(
          section.getInt("max-buy", 99999), section.getInt("max-sell", 99999));

      this.maxPurchases.put(key, maxBuySellData);

      if (shops.containsKey(key)) {
        shops.get(key).loadConfiguration(section);
        continue;
      }

      shops.put(key, new Shop(section, false));
      Main.getInstance().getLogger().config("Loaded new item into shop " + key);

    }

    config = Main.getInstance().getDataFiles().getEnchantments()
        .getConfigurationSection("enchantments");
    
    for (String key : config.getKeys(false)) {

      if (NamespacedKey.minecraft(key.toLowerCase()) == null) {
        Main.getInstance().getLogger().severe("Invalid enchantment in enchantments.yml: " + key);
        continue;
      }

      key = key.toUpperCase();
      ConfigurationSection section = config.getConfigurationSection(key);
      
      if (shops.containsKey(key)) {
        shops.get(key).loadConfiguration(section);
        continue;
      }

      if (section == null) {

        Main.getInstance().getLogger().severe("Invalid enchantment in enchantments.yml: " + key
            + " (missing section)");

        continue;
      }

      shops.put(key, new Shop(section, true));
      Main.getInstance().getLogger().config("Loaded new enchantment into shop " + key);

    }

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

  /**
   * Update the percentage changes for each shop.
   */
  public void updatePercentageChanges() {

    for (Shop shop : shops.values()) {
      shop.updatePercentageChanges(Config.getConfig().getTimePeriod());
    }

  }

  /**
   * Get the amount of buys or sells left for an item.
   * @param item The item to get the amount of buys or sells left for.
   * @param player The player to get the amount of buys or sells left for.
   * @param isBuys Whether the amount of buys or sells left is being requested.
   * @return The amount of buys or sells left for the item.
   */
  public int getPurchasesLeft(String item, OfflinePlayer player, boolean isBuys) {

    MaxBuySellData maxBuySellData = maxPurchases.get(item);

    if (maxBuySellData == null || Config.getConfig().isDisableMaxBuysSells()) {
      return 99999;
    }

    int max = isBuys ? maxBuySellData.getBuys() : maxBuySellData.getSells();
    SalePositionType type = isBuys ? SalePositionType.BUY : SalePositionType.SELL;
    List<Transaction> lastPurchases = getLastPurchases(item, player.getUniqueId(), type);

    if (lastPurchases.isEmpty()) {
      return max;
    }

    int total = 0;
    for (Transaction transaction : lastPurchases) {
      total += transaction.getAmount();
    }

    return max - total;

  }

  /**
   * Get the last purchases for an item, a player, and a type.
   * @param item The item to get the last purchases for.
   * @param uuid The player uuid to get the last purchases for.
   * @param type The type of purchases to get the last purchases for.
   * @return The last purchases for the item, player, and type.
   */
  public List<Transaction> getLastPurchases(String item, UUID uuid, SalePositionType type) {

    List<Transaction> lastPurchases = new ArrayList<Transaction>();
    
    for (Transaction transaction : recentTransactions) {

      if (transaction.getItem().equalsIgnoreCase(item) && transaction.getPlayer().equals(uuid)
            && transaction.getPosition() == type) {

        lastPurchases.add(transaction);

      }
    }

    return lastPurchases;
  }

  /**
   * Update the total player balance.
   */
  public void updateEconomyInfo() {

    double serverBalance = 0;
    double serverPlayerCount = 0;

    // Loop through all joined players.
    for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {

      // If the player is null, continue.
      if (player == null) {
        continue;
      }

      try {
        serverBalance += EconomyFunctions.getEconomy().getBalance(player);
        serverPlayerCount++;
      } catch (Exception e) {
        return;
      }

    }

    Main.getInstance().getLogger().info("Server balance: " + serverBalance);
    Main.getInstance().getLogger().info("Server player count: " + serverPlayerCount);
    economyData.get("BALANCE").update(serverBalance);
    economyData.get("POPULATION").update(serverPlayerCount);
    updateDebt();

  }

  /**
   * Update the total debt.
   */
  private void updateDebt() {

    double serverDebt = 0;

    // Loop through loans and add value to server debt.
    for (long time : loans.keySet()) {
      Loan[] data = loans.get(time);
      for (Loan loan : data) {
        serverDebt += loan.getValue();
      }
    }

    Main.getInstance().getLogger().info("Server debt: " + serverDebt);
    economyData.get("DEBT").update(serverDebt);

  }

  public double getGdp() {
    return economyData.get("GDP").getValue();
  }

  public double getBalance() {
    return economyData.get("BALANCE").getValue();
  }

  public double getPopulation() {
    return economyData.get("POPULATION").getValue();
  }

  public double getDebt() {
    return economyData.get("DEBT").getValue();
  }

  public double getSpd() {
    return economyData.get("SPD").getValue();
  }

  public double getLoss() {
    return economyData.get("LOSS").getValue();
  }


  public double getInflation() {
    return economyData.get("INFLATION").getValue();
  }


}

