![GitHub Workflow Status](https://img.shields.io/github/workflow/status/Unprotesting/Auto-Tune/Java%20CI%20with%20Maven)
![GitHub issues](https://img.shields.io/github/issues/Unprotesting/Auto-Tune)
![GitHub pull requests](https://img.shields.io/github/issues-pr/Unprotesting/Auto-Tune)
![GitHub release (latest by date)](https://img.shields.io/github/v/release/Unprotesting/Auto-Tune)
[![Discord](https://img.shields.io/discord/748222485975269508.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/bNVVPe5)


# Auto-Tune
 
 ### A powerful Minecraft Automatic-Economy Plugin for 1.14-1.16 with lots of features
   
  <img src="https://github.com/Unprotesting/Auto-Tune/blob/1.16.3/.github/AtLogo.png?raw=true" width="185"/>
  

## Features

  #### What is Auto-Tune?

Auto-Tune is a Minecraft plugin for server that aims at transforming a part of Minecraft servers often ignored and that remains undeveloped. That is the economy of Minecraft servers.

There are currently two forms of Minecraft economies that exists on modern servers. The first is vanilla which does not use plugins to assist in functionality within the economy. The second is a server-shop, vanilla blend, this method uses player-to-player trades and a server-wide shop that includes set prices for items to be bought and sold. The second method is often preferred on SMP/vanilla as it provides higher liquidity and engagement in the market than vanilla. However, on anarchy/semi-anarchy servers the vanilla-economy is provided as vanilla mechanics are often preferred.

Both methods have significant issues. Vanilla being the most obvious with incredibly low engagement in the market as trades are strictly player-to-player bartering. To fix this issue servers and plugin-makers have developed a server-economy. Plugins such as essentials and Vault’s API allows for a medium of exchange within Minecraft. This has drastically fixed a lot of issues with the bartering system within Minecraft. However, the issue of low market engagement persisted. This has had attempts to be “fixed” however, by introducing server-wide shop plugins, often in the form of shop GUI’s or sign-shops. This is the current “preferred” method of economies in Minecraft servers.

Auto-Tune aims to fix the still present problems in both methods of economies by introducing automatic dynamic pricing powered by Auto-Tune API, an API we have developed that contains various algorithms for calculating prices. Auto-Tune is a powerful, highly customizable plugin that creates a GUI-shop with server-set items that hooks into the Auto-Tune API and updates prices for items based on aggregate supply and demand. Different levels of Auto-Tune unlock access to various pricing models. However the Auto-Tune plugin contains many advanced features standalone; an integrated web-server that creates a website which displays graphs of items on the select server, automatic selling, volatility settings, sell price difference variation, and more. Our team at Auto-Tune is optimistic and passionate about Minecraft, plugin development and improving the community as a whole and are working hard at improving the plugin which is still deep in development.

An example of a graph for an item created by the exponential algorithm displayed online:

<img src="https://github.com/Unprotesting/Auto-Tune/blob/1.16.3/.github/graph.png?raw=true" width="550"/>

An example of a shop setup using Auto-Tune:

<img src="https://github.com/Unprotesting/Auto-Tune/blob/1.16.3/.github/shop.png?raw=true" width="550"/>

  #### Feature List

  - 3 Pricing Algorithim Options [Basic, Advanced, Exponential]
  - Basic and Advanced contains 5 pricing model options. [Volaility Algorithim Settings]
  - Exponential contains 7 pricing model options. [Volatility Options and algorithim, data-selection-algorithim, and more]
  - 2 forced-inflation methods with individual options [Dynamic and Static Inflation, configurable update periods]
  - 2 integrated web-servers to display prices online in graphs [Server port settings and more]
  - Configurable GUI with sizing and naming options [80+ supported item-slots]
  - Config settings can easily be modified with an in-built GUI editor
  - Configurable shops with options to lock price and sell-price-differences [For more look at shops.yml configuration below]
  - Configurable sell-price-difference
  - Sell price-difference-variation algorithim options [Update period, total time, starting-difference, ending difference]
  - Player loaning [/loan, /loans, /payloan, easy to use]
  - Configurable interest rates [Update period, amount and more]
  - Debt settings
  - GDP and GDP per capita calculation [factors in buying/selling, debt, and loaning, using /gdp]
  - Incredibly fast data-collection and creation [50000 data insertions and retrevals a second when in memory, 5000 data insertions and retrievals a second when in storage (in our tests)]
  - Data corruption protection
  - And more!
  - Coming soon (Dynamic Intrest rates, Credit scoring, Lots more GUI-shop configuration, even faster data-retrieval, updated web-server, and more (We're open to suggestions))

## Usage

  #### To use Auto-Tune

   - Note: Make sure you have a valid API key
   - 1. Download the latest version of Auto-Tune from the resources tab for your Minecraft server's Minecraft version
   - 2. Put the .jar file in your minecraft plugins folder
   - 3. Restart your server
   - 4. Edit the config.yml file inside plugins/Auto-Tune/, and enter your API key and email (check Auto-Tune API for more)
   - 5. Edit any other settings you want, in the config.yml file, especially set "pricing-model" to your API key type.
   - 6. Edit the shops.yml fiole to include items you wish to have in your shop
   - 7. Restart your server, again
   - 8. Auto-Tune will be running on your server, do /shop to view the items you set in the shops.yml file.

  #### To build the project yourself

   - 1. Clone the project to a local directory
   - 2. Go to go the the Auto-Tune folder that has pom.xml (Auto-Tune/Auto-Tune)
   - 3. Run mvn clean package
   - 4. Go to the /target/ directory and Auto-Tune-0.x.x will be there if successful

  #### Configuring config.yml
  <details>
  <summary>Config.yml</summary>
  
  Auto-Tune's Config.yml file:

      -- General Settings --  

      API key given on purchase
    api-key: 'xyz'

      Email used on signup
    email: 'xyz@gmail.com'

      Enable//Disable integrated Web Server.
    web-server-enabled: true

      Port for integrated Web Server (If enabled and on correct plan)
    port: 8123

      The maximum length in data points that the trade-short.html will show (this doesn't affect data)
    maximum-short-trade-length: 100

      Server name that will show up in commands and requests
    server-name: 'Auto-Tune Test Server'

      Automatic Pricing Model
    pricing-model: 'Basic'

      Time Period in minutes
    time-period: 10

      The amount of menu rows in the GUI shop, value of 4-6.
    menu-rows: 6

      GUI Shop Menu title
    menu-title: 'Auto-Tune Shop'

      Message sent for players with no permission
    no-permission: '&eYou do not have permission to perform this command'
      
      Enable player automatic-selling
    auto-sell-enabled: true

      How often auto-sell updates in ticks
    auto-sell-update-period: 15

      How often players are shown their auto-sell profits in ticks
    auto-sell-profit-update-period: 1200

      -- Basic/Advanced Pricing Model Settings --  

      Volatility Calulation Algorithim
      Info: A fixed rate locks the maxiumum change to a certain price
      Info: A variable rate changes the maxiumum price-change based on current price calculations
    Volatility-Algorithim: 'Variable'

      Percentage difference in sell price to buy price
    sell-price-difference: 2.5

      Maximum Volatility per Time Period for the Fixed Volatility price calculation algorithim in economy units
    Fixed-Max-Volatility: 2.00
      Minimum Volatility per Time Period for the Fixed Volatility price calculation algorithim in economy units
    Fixed-Min-Volatility: 0.05

      Maximum Volatility per Time Period for the Variable Volatility price calculation algorithim as a percentage of total price
    Variable-Max-Volatility: 3.00
      Minimum Volatility per Time Period for the Fixed Volatility price calculation algorithim in economy units
    Variable-Min-Volatility: 0.05

      -- Exponential Pricing Model Settings --  

      Info: This algorithim uses settings from the Variable-Advanced Pricing Model Settings: sell-price-difference, Variable-Max-Volatility, Variable-Min-Volatility
      Info: When setting your data selection algorithim use a site such as https://www.desmos.com/calculator
      Info: Data selection uses the equation y=m(x^z)+c, for example the default is y=0.075(x^1.6)+1.25

      'm' in equation: y=m(x^z)+c
    data-selection-m: 0.05
      'z' in equation: y=m(x^z)+c
    data-selection-z: 1.6
      'c' in equation: y=m(x^z)+c
    data-selection-c: 1.25

      -- Other Econonomy Settings --

      The symbol that appears before all currency
    currency-symbol: '$'

      Enable sell price difference variation to ease out sell price varition
    sell-price-difference-variation-enabled: false
      Starting percententage sell price difference for sell price varition
    sell-price-differnence-variation-start: 25.0
      Time in minutes until sell price reaches sell-price-difference set in pricing model settings (default 7 days)
    sell-price-variation-time-period: 10080
      Time in minutes that the sell-price-difference updates
      Info: Must be a factor of sell-price-variation-time-period or it wont work!
    sell-price-variation-update-period: 30

      Enable forced inflation in the economy
    inflation-enabled: true
      Inflation method can be Dynamic, Static or Mixed.
      Info: The dynamic method increases the prices of items in the economy by a percentage each time period
      Info: The static method adds extra values to buys
      Info: Mixed uses both methods
    inflation-method: 'Mixed'
      Time period in ticks between dynamic price increases
    dynamic-inflation-time-period: 5000
      Percentage increase in prices per time-period.
    dynamic-inflation-value: 0.0025
      Percentage increase for buy value per price calculation update period.
    static-inflation-value: 0.1

      Intrest rate per intrest-rate-update-period
      Info: This is the increase in the current debt payment per-time period
    intrest-rate: 0.01
      Time period in ticks between updates of the intrest rate for users loans
    intrest-rate-update-period: 1200

      Maximum value in $ a player can go into debt
    max-debt-value: -1000.00

      -- Other Settings --

      Enable debug mode for more info on price calculations
    debug-enabled: false

      Enable ChecksumHeaderBypass if you have issues with data retrival or corruption
    checksum-header-bypass: false
  </details>

  #### Configuring shops.yml
  
  <details>
  <summary>Shops.yml</summary>
    
   Auto-Tune's Shops.yml file:

      Set the default / starting prices for the items you want available in the shop
      Info: Make sure you put a decimal point and two digits to create a double for the shop algorithim to accept and parse to a price-value
      Info: Most of these values will be almost useless once the economy has started, to use these values again delete the data.db file
      Options: "price: <double>" set the defualt starting price of the item
      Options: "locked: <boolean>" set the price to be locked or variable (variable by default).
      Options: "sell-difference: <double>" set a custom sell-price-difference (default set in config).
    shops:
    APPLE:
        price: 100.00    <- Example of a variable item
    STICK:
        price: 50.00
    locked: true      <- Example of a locked item
    sell-difference: 0.0    <- Example of a set sell-difference item
 </details>

## Auto-Tune-API

  #### What is Auto-Tune API

Auto-Tune API is the API Auto-Tune connects to, in order to automatically calculate price data. In other words, it is the server that is the backbone of the dynamic pricing-model that powers Auto-Tune.
    
  #### How to purchase an Auto-Tune API key

To get an Auto-Tune API key please open a ticket on our discord:

[![Discord](https://img.shields.io/discord/748222485975269508.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/bNVVPe5)

  #### Auto-Tune API Pricing

Pricing at the moment will be variable based on how much we think your server having Auto-Tune will benefit its development and growth (free keys are available until 2021!)
When Auto-Tune goes paid, however, pricing will roughly be at around:
- Basic: £1.00 per 25,000 - 50,000 API requests
- Advanced: £1.00 per 20,000 - 40,000 API requests
- Exponential: £1.00 per 8,000 - 25,000 API requests

#### How to access the Auto-Tune API without the Auto-Tune plugin

For Auto-Tune users this is not neccessary to worry about, however, those who want to access the API without the need for the Auto-Tune plugin can do so.

Below is the code used by Auto-Tune to access the API
You will need to send some variables to the API for it to return a price
Those are: <String> model [Fixed|Variable (On advanced/exponential algorithms the model is fixed at Varaible)], <String> algorithm [Basic|Advanced|Exponential], <String> apikey, <String> email, <String> item [Can be anything], <Double> price, <Double> averageBuy[For Advanced/Exponential calculate the Average Buy Value], <Double> averageSell[For Advanced/Exponential calculate the Average Sell Value], <Double> maxVolatility, <Double> minVolatility

<details>
  <summary>Http Post requestor for Auto-TuneAPI Example</summary> 
  
  This code is used in Auto-Tune:
  
  ```java
    import java.io.IOException;

        import com.google.gson.JsonElement;
        import com.google.gson.JsonObject;
        import com.google.gson.JsonParser;

        import org.apache.http.HttpEntity;
        import org.apache.http.client.ClientProtocolException;
        import org.apache.http.client.methods.CloseableHttpResponse;
        import org.apache.http.client.methods.HttpPost;
        import org.apache.http.entity.StringEntity;
        import org.apache.http.impl.client.CloseableHttpClient;
        import org.apache.http.impl.client.HttpClients;
        import org.apache.http.util.EntityUtils;
        import org.json.simple.JSONObject;
        import org.json.simple.parser.ParseException;

        import unprotesting.com.github.Main;

        public class HttpPostRequestor {

            public static Double sendPostRequestUsingHttpClient(String model, String algorithm, String apikey, String email,
                    String item, Double price, Double averageBuy, Double averageSell, Double maxVolatility,
                    Double minVolatility) throws ClientProtocolException, IOException, ParseException {
                Double newPrice = price;
                CloseableHttpClient client = HttpClients.createDefault();
                HttpPost httpPost = new HttpPost("https://safe-refuge-09383.herokuapp.com");
                JSONObject json = JSONManager.returnJSONFromParams(model, algorithm, price, averageBuy, averageSell,
                        maxVolatility, minVolatility);
                StringEntity entity = new StringEntity(json.toJSONString());
                httpPost.setEntity(entity);
                httpPost.setHeader("content-type", "application/json");
                httpPost.setHeader("apikey", apikey);
                httpPost.setHeader("email", email);
                CloseableHttpResponse response = client.execute(httpPost);
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode == 200) {
                    client.close();
                }
                ;
                if (statusCode != 200) {
                    client.close();
                    Main.log("Error on status code");
                }
                ;
                Main.debugLog(response.getStatusLine().getReasonPhrase());
                HttpEntity entityResponse = response.getEntity();
                if (entityResponse != null) {
                    JsonParser parser = new JsonParser();
                    String result = EntityUtils.toString(entityResponse);
                    JsonElement jsonElement = parser.parse(result);
                    JsonObject jsonObject = jsonElement.getAsJsonObject();
                    JsonElement NewPriceElement = (jsonObject.get("newPrice"));
                    String NewPrice = NewPriceElement.getAsString();
                    newPrice = Double.parseDouble(NewPrice);
                }
                Main.debugLog("Status code: " + Integer.toString(statusCode));
                return newPrice;
            }

            // Use this method to return a Double for the price
            public static Double sendRequestForPrice(String model, String algorithm, String apikey, String email, String item,
                    Double price, Double averageBuy, Double averageSell, Double maxVolatility, Double minVolatility)
                    throws ParseException {
                Double newPrice = price;
                try {
                    newPrice = sendPostRequestUsingHttpClient(model, algorithm, apikey, email, item, price, averageBuy,
                            averageSell, maxVolatility, minVolatility);
                    return newPrice;
                    }
                    catch (IOException e){
                        e.printStackTrace();
                    }
                return newPrice;
            }
            
                    
        }
```
  
</details>
