![GitHub Workflow Status](https://img.shields.io/github/workflow/status/Unprotesting/Auto-Tune/Java%20CI%20with%20Maven)
![GitHub issues](https://img.shields.io/github/issues/Unprotesting/Auto-Tune)
![GitHub pull requests](https://img.shields.io/github/issues-pr/Unprotesting/Auto-Tune)
![GitHub release (latest by date)](https://img.shields.io/github/v/release/Unprotesting/Auto-Tune)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/2f6d82bd12af4ce490959be74d1b6149)](https://app.codacy.com/gh/Unprotesting/Auto-Tune?utm_source=github.com&utm_medium=referral&utm_content=Unprotesting/Auto-Tune&utm_campaign=Badge_Grade_Settings)
[![Discord](https://img.shields.io/discord/748222485975269508.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/bNVVPe5)

# Auto-Tune
 
 ### A Powerful Minecraft Automatic-Economy Plugin for ```1.13-1.18``` with many features!
   
  <img src="https://github.com/Unprotesting/Auto-Tune/blob/master/.github/AtLogo.png?raw=true" width="185"/>
  

## Features

  #### What is Auto-Tune?

Auto-Tune is a Minecraft plugin for server that aims at transforming a part of Minecraft servers often ignored and that remains undeveloped. That is the economy of Minecraft servers.

There are currently two forms of Minecraft economies that exists on modern servers. The first is vanilla which does not use plugins to assist in functionality within the economy. The second is a server-shop, vanilla blend, this method uses player-to-player trades and a server-wide shop that includes set prices for items to be bought and sold. The second method is often preferred on SMP/vanilla as it provides higher liquidity and engagement in the market than vanilla. However, on anarchy/semi-anarchy servers the vanilla-economy is provided as vanilla mechanics are often preferred.

Both methods have significant issues. Vanilla being the most obvious with incredibly low engagement in the market as trades are strictly player-to-player bartering. To fix this issue servers and plugin-makers have developed a server-economy. Plugins such as essentials and Vault’s API allows for a medium of exchange within Minecraft. This has drastically fixed a lot of issues with the bartering system within Minecraft. However, the issue of low market engagement persisted. This has had attempts to be “fixed” however, by introducing server-wide shop plugins, often in the form of shop GUI’s or sign-shops. This is the current “preferred” method of economies in Minecraft servers.

Auto-Tune aims to fix the still present problems in both methods of economies by introducing automatic dynamic pricing powered by Auto-Tune API, an API we have developed that contains various algorithms for calculating prices. Auto-Tune is a powerful, highly customizable plugin that creates a GUI-shop with server-set items that hooks into the Auto-Tune API and updates prices for items based on aggregate supply and demand. Different levels of Auto-Tune unlock access to various pricing models. However the Auto-Tune plugin contains many advanced features standalone; an integrated web-server that creates a website which displays graphs of items on the select server, automatic selling, volatility settings, sell price difference variation, and more. Our team at Auto-Tune is optimistic and passionate about Minecraft, plugin development and improving the community as a whole and are working hard at improving the plugin which is still deep in development.

An example of a graph for an item created by the algorithm displayed online:

<img src="https://github.com/Unprotesting/Auto-Tune/blob/master/.github/graph.png?raw=true" width="550"/>

An example of a shop setup using Auto-Tune:

<img src="https://github.com/Unprotesting/Auto-Tune/blob/master/.github/ShopDisplay.gif?raw=true" width="550"/>

  #### Feature List

  - ```Advanced pricing model.```
  - ```Contains 7 pricing model options. [Volatility Options and algorithm, data-selection-algorithm, and more].```
  - ```2 integrated web-servers to display prices online in graphs [Server port settings and more].```
  - ```Configurable GUI with sizing, positioning and naming options.```
  - ```Configurable shops with options to lock price and sell-price-differences [For more look at shops.yml configuration below].```
  - ```Configurable sell-price-difference.```
  - ```Stored detailed history of transactions.```
  - ```Sell price-difference-variation algorithm options [Update period, total time, starting-difference, ending difference].```
  - ```Player loaning including an easy loaning GUI.```
  - ```Configurable interest rates [Update period, amount and more].```
  - ```Debt settings.```
  - ```Exploit protection.```
  - ```GDP and GDP per capita calculation [factors in buying/selling, debt, and loaning, using /gdp].```
  - ```Much more economy info such as server-balance debt, loss, and inflation.```
  - ```Incredibly fast data-collection and creation.```
  - ```Data corruption protection.```
  - ```Configurable messages.```
  - ```Fully configurable tutorial.```
  - ```Configurable automatic selling command for players.```
  - ```Data export and import.```
  - ```And more!```
  - ```Many more features coming soon.```

## Usage

  #### To use Auto-Tune

   - Note: Make sure you have a valid API key.
   - Download the latest version of Auto-Tune from the resources tab for your Minecraft server's Minecraft version - Make sure the dependencies are also installed ([Vault](https://www.spigotmc.org/resources/vault.34315/) and any economy plugin such as [Essentials](https://essentialsx.net)). An optional dependency is [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/). 
   - Put the .jar file in your Minecraft ```/plugins``` folder.
   - Restart your server.
   - Edit the ```config.yml``` file inside ```/plugins/Auto-Tune/```, and enter your API key and email (check Auto-Tune API for more).
   - Edit any other settings you want, in the ```config.yml``` file.
   - Edit the ```shops.yml``` file to include items you wish to have in your shop.
   - Restart your server, again.
   - Auto-Tune will be running on your server, do ```/shop``` to view the items you set in the ```shops.yml``` file.
   - For more information view our [Wiki](https://github.com/Unprotesting/Auto-Tune/wiki).

  #### To build the project yourself

   - Clone the project to a local directory using ```git clone https://github.com/Unprotesting/Auto-Tune.git ```.
   - Run ```cd Auto-Tune``` to enter the Auto-Tune folder.
   - Run ```./gradlew build```.
   - Navigate to the ```/builds/libs/``` directory and Auto-Tune-0.x.x will be there if successful.

  In addition all the latest builds are available under the [Actions](https://github.com/Unprotesting/Auto-Tune/actions) section of the github.
  Simply go the latest action and download the 'artifact' file, then unzip it, and the .jar will be inside.

  #### Configuring config.yml
  <details>
  <summary>Config.yml</summary>
  
  Auto-Tune's Config.yml file:

```

# ░█████╗░██╗░░░██╗████████╗░█████╗░░░░░░░████████╗██╗░░░██╗███╗░░██╗███████╗
# ██╔══██╗██║░░░██║╚══██╔══╝██╔══██╗░░░░░░╚══██╔══╝██║░░░██║████╗░██║██╔════╝
# ███████║██║░░░██║░░░██║░░░██║░░██║█████╗░░░██║░░░██║░░░██║██╔██╗██║█████╗░░
# ██╔══██║██║░░░██║░░░██║░░░██║░░██║╚════╝░░░██║░░░██║░░░██║██║╚████║██╔══╝░░
# ██║░░██║╚██████╔╝░░░██║░░░╚█████╔╝░░░░░░░░░██║░░░╚██████╔╝██║░╚███║███████╗
# ╚═╝░░╚═╝░╚═════╝░░░░╚═╝░░░░╚════╝░░░░░░░░░░╚═╝░░░░╚═════╝░╚═╝░░╚══╝╚══════╝

##  -- Auto-Tune Config File --  ##

## See the wiki for info on each setting: https://github.com/Unprotesting/Auto-Tune/wiki

##  -- General Settings --  ##

##  API key given on sign-up
api-key: 'xyz'

##  Email used on sign-up
email: 'xyz@gmail.com'

##  Enable/Disable integrated Web Server.
##  Info: Use /trade to view the web-server
web-server-enabled: true

##  Port for integrated Web Server (If enabled)
##  Make sure to port-forward and disable firewalls for this port.
port: 8123

##  The maximum length in data points that the trade-short.html will show (this doesn't affect data)
##  Info: When the time-period is set to 30, a maximum-short-trade-length of 48 is one day.
maximum-short-trade-length: 50

##  Time Period in minutes
##  Info: When decreasing or increasing this adjust your volatility settings accordingly
time-period: 30

##  GUI Shop Menu title
menu-title: 'Shop'

##  GUI Shop Menu background
##  Info: Use 'NONE' for no background
background: 'BLACK_STAINED_GLASS_PANE'

##  How often auto-sell updates in ticks
##  Info: Set this higher if few players use autosell
##  Info: Set it lower if many players use autosell
auto-sell-update-period: 10

##  How often players are shown their auto-sell profits in ticks
auto-sell-profit-update-period: 600

##  Global number format
number-format: '###,###,###,###,##0.00'

##  Enable Enchantments
enable-enchantments: true

##  -- Pricing Model Settings --  ##

##  Percentage difference in sell price to buy price
sell-price-difference: 10.0

##  Maximum Volatility per Time Period for the Variable Volatility price calculation algorithm as a percentage of total price
max-volatility: 0.5

##  Minimum Volatility per Time Period for the Fixed Volatility price calculation algorithm in economy units
min-volatility: 0.05

##  -- Data Selection Settings --  ##

##  Info: When setting your data selection algorithm use a site such as https://www.desmos.com/calculator
##  Info: Data selection uses the equation y=m(x^z)+c, for example the default is y=0.075(x^(1.6))+0.55

##  'm' in equation: y=m(x^z)+c
data-selection-m: 0.05

##  'z' in equation: y=m(x^z)+c
data-selection-z: 1.75

##  'c' in equation: y=m(x^z)+c
data-selection-c: 0.55

##  -- Other Economy Settings --

##  Minimum players needed, to be online, for prices to update
##  Info: It is recommended to keep this above 0
update-prices-threshold: 1

##  The symbol that appears before all currency
currency-symbol: '$'

##  Enable sell price difference variation to ease out sell price variation
sell-price-difference-variation-enabled: true

##  Starting percentage sell price difference for sell price variation
sell-price-difference-variation-start: 30.0

##  Time in minutes until sell price reaches sell-price-difference set in pricing model settings (default 4 weeks)
sell-price-variation-time-period: 43200

##  Time in minutes that the sell-price-difference updates
sell-price-variation-update-period: 30

##  Interest rate per interest-rate-update-period
##  Info: This is the increase in the current debt payment per-time period
interest-rate: 0.025

##  Time period in ticks between updates of the interest rate for users loans
interest-rate-update-period: 1200

##  Disable the max-buy/max-sells for items
##  Info: Don't do this unless you know what your doing.
##  Info: If this is enabled, it is likely to lead to exploitation on servers with under 10 concurrent players
disable-max-buys-sells: false

##  The lowest value in $ a player can go into debt
##  For example if a player has $3,000 and this is set to 1000 a player can take out a loan up to $2,000
max-debt-value: 1000.0

##  The percentage value to decrease items sold with enchantments
##  Info: Stacked enchantments etc. can become very expensive so a number between 30% - 60% is usually fine
##  Info: This doesn't affect buys
enchantment-limiter: 50.0

##  The percentage value to decrease items sold with a loss in durability
##  Info: This is applied ON TOP of the durability algorithm to limit the exploit-ability of selling tools
durability-limiter: 10.0

##  -- Other Settings --

##  Enable debug mode for more info on price calculations
debug-enabled: false

##  Ignore AFK players in price calculations/online checks
##  Info: Turning this on will mean the price will only update when there's a player that is not AFK
##  Info: This only applies to essentials AFK and you must have essentials installed for this to work
ignore-afk: true

##  Enable ChecksumHeaderBypass if you have issues with data retrieval or corruption
##  Info: This is a very rare occurrence and should only be used if you have issues with data retrieval or corruption
checksum-header-bypass: true

##  Enable the Auto-Tune tutorial for players (the messages that are displayed to players periodically)
##  Keep this on to encourage purchasing turn it off if it is distracting
tutorial: true

##  Time in seconds between messages
tutorial-message-period: 325

##  Data storage location
data-location: 'plugins/Auto-Tune/'

##  Enable transactions
##  Info: This will result in much larger file sizes and additional transaction files but more stability and improved data-protection
##  Info: This is off by default as the default data protection will be fine for most servers
data-transactions: false

##  Read initial price data and buys/sells from trade.csv
##  Stop server, set this to true, delete data.db, import your trade.csv, start the server, set this to false, restart server
read-from-csv: false

```
  </details>

  #### Configuring shops.yml
  
  <details>
  <summary>Shops.yml</summary>
    
   Auto-Tune's Shops.yml file:

```

# ░█████╗░██╗░░░██╗████████╗░█████╗░░░░░░░████████╗██╗░░░██╗███╗░░██╗███████╗  
# ██╔══██╗██║░░░██║╚══██╔══╝██╔══██╗░░░░░░╚══██╔══╝██║░░░██║████╗░██║██╔════╝  
# ███████║██║░░░██║░░░██║░░░██║░░██║█████╗░░░██║░░░██║░░░██║██╔██╗██║█████╗░░  
# ██╔══██║██║░░░██║░░░██║░░░██║░░██║╚════╝░░░██║░░░██║░░░██║██║╚████║██╔══╝░░  
# ██║░░██║╚██████╔╝░░░██║░░░╚█████╔╝░░░░░░░░░██║░░░╚██████╔╝██║░╚███║███████╗  
# ╚═╝░░╚═╝░╚═════╝░░░░╚═╝░░░░╚════╝░░░░░░░░░░╚═╝░░░░╚═════╝░╚═╝░░╚══╝╚══════╝  

##  -- Auto-Tune Shops File --  ##

## See the wiki for info on each setting: https://github.com/Unprotesting/Auto-Tune/wiki

##  Shop sections configuration
sections:
  'Natural Resources':
    ##  Block that the section image is displayed as
    block: 'IRON_ORE'
    ##  Background for sub-menu
    ##  Use 'NONE' for no background
    background: 'GRAY_STAINED_GLASS_PANE'
    ##  Position in Main Menu GUI
    position: 10
    ##  Optional - set to disabled to disable the back button when running "/shop <shop-section>".
    ##  This wont disable the back button when running just "/shop"
    back-menu-button-enabled: true
    ##  Set the display name for the section
    display-name: '&6Natural Resources'
  'Blocks':
    block: 'STONE'
    position: 12
    background: 'GRAY_STAINED_GLASS_PANE'
    back-menu-button-enabled: true
    display-name: '&6Blocks'
  'Food':
    block: 'COOKED_BEEF'
    position: 14
    background: 'GRAY_STAINED_GLASS_PANE'
    back-menu-button-enabled: true
    display-name: '&6Food'
  'Farming':
    block: 'OAK_SAPLING'
    position: 16
    background: 'GRAY_STAINED_GLASS_PANE'
    back-menu-button-enabled: true
    display-name: '&6Farming'
  'Tools':
    block: 'DIAMOND_PICKAXE'
    position: 20
    background: 'GRAY_STAINED_GLASS_PANE'
    back-menu-button-enabled: true
    display-name: '&6Tools'
  'Other':
    block: 'STICK'
    position: 24
    background: 'GRAY_STAINED_GLASS_PANE'
    back-menu-button-enabled: true
    display-name: '&6Other'

##  Set the default / starting prices for the items you want available in the shop.
##  IMPORTANT: Thee prices of these items can't be changed once the database has been created. 
##  IMPORTANT: To change the price of items use /at price <item-name> <double> or reset the data.db file.
##  Info: Material names are available here: https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Material.html.
##  IMPORTANT: Material names are case sensitive! (All caps and underscores)
##  Options: 'price: <double>' set the default starting price of the item.
##  Options: 'section: <string>' set the section of this item.
##  Options: 'locked: <boolean>' set the price to be locked or variable (variable by default).
##  Options: 'sell-difference: <double>' set a custom sell-price-difference (default set in config).
##  Options: 'max-buy': <integer> set a maximum quantity that can be purchased in a time-period.
##  Options: 'max-sell': <integer> set a maximum quantity that can be sold in a time-period.
##  Options: 'max-volatility: <double>' set the maximum volatility per time-period for this individual item.
##  Options: 'min-volatility: <double>' set the minimum volatility per time-period for this individual item.
##  Options: 'display-name': <string>' set the display name of the item.
##  Options: 'collect-first-setting': <string>' ('NONE'|'SERVER_WIDE'|'EACH_PLAYER') whether the item needs to be collected before it can be purchased.


  ##  Example:
  ##  GRASS_BLOCK:
  ##    price: 50.00
  ##    section: 'Blocks'      This item will exist within the 'Blocks' shop-section
  ##    locked: true      <- Example of a locked item
  ##    sell-difference: 0.0    <- Example of a set sell-difference item
  ##    max-buy: 10       <- Only 10 items can be bought each time-period - you can increase this on large servers
  ##    max-sell: 12       <- Only 12 items can be sold each time-period - you can increase this on large servers
  ##    max-volatility: 0.25       <- Example of an item with a lower max-volatility
  ##    min-volatility: 0.05       <- Example of an item with a higher min-volatility
  ##    display-name: '&aGrass'      <- Example of a custom display name (with a green color code)
  ##    collect-first-setting: 'EACH_PLAYER' <- Example of a custom collect-first-setting

shops:
  COAL:
    price: 15.00
    max-buy: 80
    max-sell: 115
    section: 'Natural Resources'
  IRON_INGOT:
    price: 17.00
    max-buy: 75
    max-sell: 100
    section: 'Natural Resources'
  COPPER_INGOT:
    price: 20.00
    max-buy: 64
    max-sell: 80
    section: 'Natural Resources'
  GOLD_INGOT:
    price: 25.00
    max-buy: 45
    max-sell: 75
    section: 'Natural Resources'
  DIAMOND:
    price: 325.00
    max-buy: 15
    max-sell: 32
    section: 'Natural Resources'
  AMETHYST_SHARD:
    price: 22.00
    max-buy: 32
    max-sell: 45
    section: 'Natural Resources'
  LAPIS_LAZULI:
    price: 12.00
    max-buy: 128
    max-sell: 256
    section: 'Natural Resources'
  EMERALD:
    price: 145.00
    max-buy: 12
    max-sell: 32
    section: 'Natural Resources'
  QUARTZ:
    price: 11.00
    max-buy: 40
    max-sell: 125
    section: 'Natural Resources'
  REDSTONE:
    price: 8.00
    max-buy: 80
    max-sell: 128
    section: 'Natural Resources'
  NETHERITE_INGOT:
    price: 8500.00
    max-buy: 1
    max-sell: 2
    section: 'Natural Resources'
  GLOWSTONE_DUST:
    price: 4.00
    max-buy: 128
    max-sell: 256
    section: 'Natural Resources'
  DIRT:
    price: 0.5
    max-buy: 512
    max-sell: 1024
    section: 'Blocks'
  COBBLESTONE:
    price: 1.00
    max-buy: 256
    max-sell: 800
    section: 'Blocks'
  STONE:
    price: 2.00
    max-buy: 128
    max-sell: 256
    section: 'Blocks'
  GRAVEL:
    price: 3.00
    max-buy: 128
    max-sell: 256
    section: 'Blocks'
  SAND:
    price: 1.00
    max-buy: 128
    max-sell: 500
    section: 'Blocks'
  SANDSTONE:
    price: 4.00
    max-buy: 100
    max-sell: 250
    section: 'Blocks'
  OBSIDIAN:
    price: 15.00
    max-buy: 20
    max-sell: 64
    section: 'Blocks'
  END_STONE:
    price: 3.50
    max-buy: 80
    max-sell: 200
    section: 'Blocks'
  NETHERRACK:
    price: 1.00
    max-buy: 256
    max-sell: 1024
    section: 'Blocks'
  WHITE_WOOL:
    price: 6.50
    max-buy: 64
    max-sell: 80
    section: 'Blocks'
  OAK_LOG:
    price: 8.00
    max-buy: 90
    max-sell: 256
    section: 'Blocks'
  MOSS_BLOCK:
    price: 35.00
    max-buy: 15
    max-sell: 32
    section: 'Blocks'
  CACTUS:
    price: 3.00
    max-buy: 64
    max-sell: 200
    section: 'Farming'
  VINE:
    price: 2.00
    max-buy: 64
    max-sell: 256
    section: 'Farming'
  CARROT:
    price: 4.00
    max-buy: 80
    max-sell: 256
    section: 'Food'
  MELON_SLICE:
    price: 1.75
    max-buy: 85
    max-sell: 175
    section: 'Food'
  POTATO:
    price: 2.25
    max-buy: 80
    max-sell: 120
    section: 'Food'
  APPLE:
    price: 4.00
    max-buy: 64
    max-sell: 100
    section: 'Food'
  WHEAT:
    price: 2.00
    max-buy: 128
    max-sell: 256
    section: 'Farming'
  SUGAR_CANE:
    price: 1.50
    max-buy: 164
    max-sell: 256
    section: 'Farming'
  BEEF:
    price: 2.50
    max-buy: 64
    max-sell: 150
    section: 'Food'
  PORKCHOP:
    price: 2.25
    max-buy: 64
    max-sell: 150
    section: 'Food'
  CHICKEN:
    price: 1.45
    max-buy: 64
    max-sell: 150
    section: 'Food'
  RABBIT:
    price: 2.50
    max-buy: 64
    max-sell: 150
    section: 'Food'
  MUTTON:
    price: 2.00
    max-buy: 64
    max-sell: 150
    section: 'Food'
  PUMPKIN:
    price: 8.00
    max-buy: 64
    max-sell: 128
    section: 'Farming'
  COD:
    price: 6.00
    max-buy: 96
    max-sell: 124
    section: 'Food'
  WHEAT_SEEDS:
    price: 1.25
    max-buy: 80
    max-sell: 200
    section: 'Farming'
  NETHER_WART:
    price: 4.00
    max-buy: 64
    max-sell: 200
    section: 'Farming'
  FLINT:
    price: 4.00
    max-buy: 64
    max-sell: 128
    section: 'Other'
  GUNPOWDER:
    price: 12.50
    max-buy: 64
    max-sell: 128
    section: 'Other'
  LEATHER:
    price: 20.00
    max-buy: 64
    max-sell: 128
    section: 'Other'
  INK_SAC:
    price: 2.50
    max-buy: 32
    max-sell: 64
    section: 'Other'
  FEATHER:
    price: 4.00
    max-buy: 32
    max-sell: 64
    section: 'Other'
  BONE:
    price: 6.00
    max-buy: 64
    max-sell: 100
    section: 'Other'
  STRING:
    price: 4.00
    max-buy: 64
    max-sell: 100
    section: 'Other'
  ROTTEN_FLESH:
    price: 0.50
    max-buy: 64
    max-sell: 128
    section: 'Food'
  SPIDER_EYE:
    price: 7.00
    max-buy: 64
    max-sell: 128
    section: 'Other'
  SLIME_BALL:
    price: 25.00
    max-buy: 32
    max-sell: 64
    section: 'Other'
  ENDER_PEARL:
    price: 45.00
    max-buy: 8
    max-sell: 20
    section: 'Other'
  NETHER_STAR:
    price: 15000.00
    max-buy: 1
    max-sell: 1
    section: 'Other'
  BLAZE_ROD:
    price: 85.00
    max-buy: 6
    max-sell: 15
    section: 'Other'
  ARROW:
    price: 2.00
    max-buy: 64
    max-sell: 128
    section: 'Other'
  OAK_PLANKS:
    price: 0.5
    max-buy: 128
    max-sell: 256
    section: 'Blocks'
  BIRCH_PLANKS:
    price: 0.5
    max-buy: 128
    max-sell: 256
    section: 'Blocks'
  SPRUCE_PLANKS:
    price: 0.5
    max-buy: 128
    max-sell: 256
    section: 'Blocks'
  JUNGLE_PLANKS:
    price: 0.5
    max-buy: 128
    max-sell: 256
    section: 'Blocks'
  DARK_OAK_PLANKS:
    price: 0.5
    max-buy: 128
    max-sell: 256
    section: 'Blocks'
  ACACIA_PLANKS:
    price: 0.5
    max-buy: 128
    max-sell: 256
    section: 'Blocks'
  OAK_SAPLING:
    price: 2.5
    max-buy: 32
    max-sell: 64
    section: 'Farming'
  STICK:
    price: 0.2
    max-buy: 64
    max-sell: 128
    section: 'Other'
  BOOK:
    price: 22.0
    max-buy: 20
    max-sell: 32
    section: 'Other'
  WOODEN_PICKAXE:
    price: 8.0
    max-buy: 4
    max-sell: 8
    section: 'Tools'
  WOODEN_AXE:
    price: 7.5
    max-buy: 4
    max-sell: 8
    section: 'Tools'
  WOODEN_SWORD:
    price: 6.5
    max-buy: 4
    max-sell: 8
    section: 'Tools'
  WOODEN_SHOVEL:
    price: 3.5
    max-buy: 4
    max-sell: 8
    section: 'Tools'
  WOODEN_HOE:
    price: 3.5
    max-buy: 4
    max-sell: 8
    section: 'Tools'
  STONE_PICKAXE:
    price: 11.0
    max-buy: 4
    max-sell: 8
    section: 'Tools'
  STONE_AXE:
    price: 10.5
    max-buy: 4
    max-sell: 8
    section: 'Tools'
  STONE_SWORD:
    price: 9.0
    max-buy: 4
    max-sell: 8
    section: 'Tools'
  STONE_SHOVEL:
    price: 5.0
    max-buy: 4
    max-sell: 8
    section: 'Tools'
  STONE_HOE:
    price: 5.0
    max-buy: 4
    max-sell: 8
    section: 'Tools'
  GOLDEN_PICKAXE:
    price: 12.0
    max-buy: 3
    max-sell: 8
    section: 'Tools'
  GOLDEN_AXE:
    price: 11.5
    max-buy: 3
    max-sell: 8
    section: 'Tools'
  GOLDEN_SWORD:
    price: 10.0
    max-buy: 3
    max-sell: 8
    section: 'Tools'
  GOLDEN_SHOVEL:
    price: 6.0
    max-buy: 3
    max-sell: 8
    section: 'Tools'
  GOLDEN_HOE:
    price: 6.0
    max-buy: 3
    max-sell: 8
    section: 'Tools'
  IRON_PICKAXE:
    price: 18.0
    max-buy: 3
    max-sell: 6
    section: 'Tools'
  IRON_AXE:
    price: 17.5
    max-buy: 3
    max-sell: 6
    section: 'Tools'
  IRON_SWORD:
    price: 13.0
    max-buy: 3
    max-sell: 6
    section: 'Tools'
  IRON_SHOVEL:
    price: 7.0
    max-buy: 3
    max-sell: 6
    section: 'Tools'
  IRON_HOE:
    price: 7.0
    max-buy: 3
    max-sell: 6
    section: 'Tools'
  DIAMOND_PICKAXE:
    price: 260.0
    max-buy: 2
    max-sell: 3
    section: 'Tools'
  DIAMOND_AXE:
    price: 250.0
    max-buy: 2
    max-sell: 3
    section: 'Tools'
  DIAMOND_SWORD:
    price: 180.0
    max-buy: 2
    max-sell: 3
    section: 'Tools'
  DIAMOND_SHOVEL:
    price: 110.0
    max-buy: 2
    max-sell: 3
    section: 'Tools'
  DIAMOND_HOE:
    price: 110.0
    max-buy: 2
    max-sell: 3
    section: 'Tools'
  LEATHER_HELMET:
    price: 22.50
    max-buy: 2
    max-sell: 4
    section: 'Tools'
  LEATHER_CHESTPLATE:
    price: 46.00
    max-buy: 2
    max-sell: 3
    section: 'Tools'
  LEATHER_LEGGINGS:
    price: 25.50
    max-buy: 2
    max-sell: 3
    section: 'Tools'
  LEATHER_BOOTS:
    price: 20.00
    max-buy: 2
    max-sell: 4
    section: 'Tools'
  IRON_HELMET:
    price: 45.50
    max-buy: 2
    max-sell: 4
    section: 'Tools'
  IRON_CHESTPLATE:
    price: 86.00
    max-buy: 2
    max-sell: 3
    section: 'Tools'
  IRON_LEGGINGS:
    price: 65.50
    max-buy: 2
    max-sell: 3
    section: 'Tools'
  IRON_BOOTS:
    price: 38.00
    max-buy: 2
    max-sell: 4
    section: 'Tools'
  GOLDEN_HELMET:
    price: 40.50
    max-buy: 2
    max-sell: 4
    section: 'Tools'
  GOLDEN_CHESTPLATE:
    price: 80.00
    max-buy: 2
    max-sell: 3
    section: 'Tools'
  GOLDEN_LEGGINGS:
    price: 60.50
    max-buy: 2
    max-sell: 3
    section: 'Tools'
  GOLDEN_BOOTS:
    price: 30.00
    max-buy: 2
    max-sell: 4
    section: 'Tools'
  DIAMOND_HELMET:
    price: 425.50
    max-buy: 2
    max-sell: 4
    section: 'Tools'
  DIAMOND_CHESTPLATE:
    price: 900.00
    max-buy: 2
    max-sell: 3
    section: 'Tools'
  DIAMOND_LEGGINGS:
    price: 620.50
    max-buy: 2
    max-sell: 3
    section: 'Tools'
  DIAMOND_BOOTS:
    price: 350.00
    max-buy: 2
    max-sell: 4
    section: 'Tools'
  FISHING_ROD:
    price: 25.00
    max-buy: 3
    max-sell: 4
    section: 'Tools'
  BOW:
    price: 28.00
    max-buy: 3
    max-sell: 4
    section: 'Tools'

```
 </details>

## Auto-Tune-API

  #### What is Auto-Tune API

Auto-Tune API is the API Auto-Tune connects to in order to start the server. The pricing mechanism is now local.
It will be used in the future to perform many tasks.
You still need an API key, for every version of Auto-Tune.
    
  #### How to get an Auto-Tune API key

Auto-Tune API key's are free.
To get an Auto-Tune API key please open a ticket on our discord:

[![Discord](https://img.shields.io/discord/748222485975269508.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/bNVVPe5).