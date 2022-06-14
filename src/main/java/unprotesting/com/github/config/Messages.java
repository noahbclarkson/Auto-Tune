package unprotesting.com.github.config;

import java.util.List;

import lombok.Getter;

import org.bukkit.configuration.file.FileConfiguration;

import unprotesting.com.github.Main;

@Getter
public class Messages {

  @Getter
  private static Messages messages;

  private List<String> onJoin;
  private List<String> tutorial;
  private String notEnoughMoney;
  private String runOutOfBuys;
  private String notEnoughSpace;
  private String notUnlocked;
  private String notInShop;
  private String shopPurchase;
  private String doNotHaveItem;
  private String runOutOfSells;
  private String shopSell;
  private String holdItemInHand;
  private String notEnoughMoneyEnchantments;
  private String enchantmentError;
  private String enchantmentPurchase;
  private String cannotSellCustom;
  private String sellCustomItem;
  private String autoSellProfitUpdate;
  private String loanSuccess;
  private String noPermission;

  public Messages() {
    messages = this;
    loadMessages();
  }

  private void loadMessages() {

    FileConfiguration config = Main.getInstance().getDataFiles().getMessages();
    onJoin = config.getStringList("on-join");
    tutorial = config.getStringList("tutorial");
    notEnoughMoney = config.getString("not-enough-money");
    runOutOfBuys = config.getString("run-out-of-buys");
    notEnoughSpace = config.getString("not-enough-space");
    notUnlocked = config.getString("not-unlocked");
    notInShop = config.getString("not-in-shop");
    shopPurchase = config.getString("shop-purchase");
    doNotHaveItem = config.getString("do-not-have-item");
    runOutOfSells = config.getString("run-out-of-sells");
    shopSell = config.getString("shop-sell");
    holdItemInHand = config.getString("hold-item-in-hand");
    notEnoughMoneyEnchantments = config.getString("not-enough-money-enchantments");
    enchantmentError = config.getString("enchantment-error");
    enchantmentPurchase = config.getString("enchantment-purchase");
    cannotSellCustom = config.getString("cannot-sell-custom");
    sellCustomItem = config.getString("sell-custom-item");
    autoSellProfitUpdate = config.getString("autosell-profit-update");
    loanSuccess = config.getString("loan-success");
    noPermission = config.getString("no-permission");

  }
  
}
