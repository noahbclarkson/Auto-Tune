![GitHub Workflow Status](https://img.shields.io/github/workflow/status/Unprotesting/Auto-Tune/Java%20CI%20with%20Maven)
![GitHub issues](https://img.shields.io/github/issues/Unprotesting/Auto-Tune)
![GitHub pull requests](https://img.shields.io/github/issues-pr/Unprotesting/Auto-Tune)
![GitHub release (latest by date)](https://img.shields.io/github/v/release/Unprotesting/Auto-Tune)
[![Discord](https://img.shields.io/discord/748222485975269508.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/bNVVPe5)


# Auto-Tune
 
 ### A Powerful Minecraft Automatic-Economy Plugin for 1.14-1.16 with Many Features!
   
  <img src="https://github.com/Unprotesting/Auto-Tune/blob/1.16.4/.github/AtLogo.png?raw=true" width="185"/>
  

## Features

  #### What is Auto-Tune?

Auto-Tune is a Minecraft plugin for server that aims at transforming a part of Minecraft servers often ignored and that remains undeveloped. That is the economy of Minecraft servers.

There are currently two forms of Minecraft economies that exists on modern servers. The first is vanilla which does not use plugins to assist in functionality within the economy. The second is a server-shop, vanilla blend, this method uses player-to-player trades and a server-wide shop that includes set prices for items to be bought and sold. The second method is often preferred on SMP/vanilla as it provides higher liquidity and engagement in the market than vanilla. However, on anarchy/semi-anarchy servers the vanilla-economy is provided as vanilla mechanics are often preferred.

Both methods have significant issues. Vanilla being the most obvious with incredibly low engagement in the market as trades are strictly player-to-player bartering. To fix this issue servers and plugin-makers have developed a server-economy. Plugins such as essentials and Vault’s API allows for a medium of exchange within Minecraft. This has drastically fixed a lot of issues with the bartering system within Minecraft. However, the issue of low market engagement persisted. This has had attempts to be “fixed” however, by introducing server-wide shop plugins, often in the form of shop GUI’s or sign-shops. This is the current “preferred” method of economies in Minecraft servers.

Auto-Tune aims to fix the still present problems in both methods of economies by introducing automatic dynamic pricing powered by Auto-Tune API, an API we have developed that contains various algorithms for calculating prices. Auto-Tune is a powerful, highly customizable plugin that creates a GUI-shop with server-set items that hooks into the Auto-Tune API and updates prices for items based on aggregate supply and demand. Different levels of Auto-Tune unlock access to various pricing models. However the Auto-Tune plugin contains many advanced features standalone; an integrated web-server that creates a website which displays graphs of items on the select server, automatic selling, volatility settings, sell price difference variation, and more. Our team at Auto-Tune is optimistic and passionate about Minecraft, plugin development and improving the community as a whole and are working hard at improving the plugin which is still deep in development.

An example of a graph for an item created by the exponential algorithm displayed online:

<img src="https://github.com/Unprotesting/Auto-Tune/blob/1.16.4/.github/graph.png?raw=true" width="550"/>

An example of a shop setup using Auto-Tune:

<img src="https://github.com/Unprotesting/Auto-Tune/blob/1.16.4/.github/ShopDisplay.gif?raw=true" width="550"/>

  #### Feature List

  - 3 Pricing Algorithim Options [Basic, Advanced, Exponential]
  - Basic and Advanced contains 5 pricing model options. [Volaility Algorithim Settings]
  - Exponential contains 7 pricing model options. [Volatility Options and algorithim, data-selection-algorithim, and more]
  - 2 forced-inflation methods with individual options [Dynamic and Static Inflation, configurable update periods]
  - 2 integrated web-servers to display prices online in graphs [Server port settings and more]
  - Configurable GUI with sizing and naming options
  - Config settings can easily be modified with an in-built GUI editor
  - Configurable shops with options to lock price and sell-price-differences [For more look at shops.yml configuration below]
  - Configurable sell-price-difference
  - Sell price-difference-variation algorithim options [Update period, total time, starting-difference, ending difference]
  - Player loaning including an easy loaning GUI with /loan
  - Configurable interest rates [Update period, amount and more]
  - Debt settings
  - Exploit protection
  - GDP and GDP per capita calculation [factors in buying/selling, debt, and loaning, using /gdp]
  - Incredibly fast data-collection and creation [50000 data insertions and retrevals a second when in memory, 5000 data insertions and retrievals a second when in storage (in our tests)]
  - Data corruption protection
  - And more!
  - Coming soon (Dynamic Intrest rates, Credit scoring, Lots more GUI-shop configuration, even faster data-retrieval, updated web-server, and more (We're open to suggestions))

## Usage

  #### To use Auto-Tune

   - Note: Make sure you have a valid API key
   - 1. Download the latest version of Auto-Tune from the resources tab for your Minecraft server's Minecraft version - Make sure the dependencies are also installed (Vault - [https://www.spigotmc.org/resources/vault.34315/] and Essentials - [https://essentialsx.net])
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

 ░█████╗░██╗░░░██╗████████╗░█████╗░░░░░░░████████╗██╗░░░██╗███╗░░██╗███████╗
 ██╔══██╗██║░░░██║╚══██╔══╝██╔══██╗░░░░░░╚══██╔══╝██║░░░██║████╗░██║██╔════╝
 ███████║██║░░░██║░░░██║░░░██║░░██║█████╗░░░██║░░░██║░░░██║██╔██╗██║█████╗░░
 ██╔══██║██║░░░██║░░░██║░░░██║░░██║╚════╝░░░██║░░░██║░░░██║██║╚████║██╔══╝░░
 ██║░░██║╚██████╔╝░░░██║░░░╚█████╔╝░░░░░░░░░██║░░░╚██████╔╝██║░╚███║███████╗
 ╚═╝░░╚═╝░╚═════╝░░░░╚═╝░░░░╚════╝░░░░░░░░░░╚═╝░░░░╚═════╝░╚═╝░░╚══╝╚══════╝


  -- General Settings --  

  API key given on purchase/signup
api-key: 'xyz'

  Email used on signup
email: 'xyz@gmail.com'

  Enable/Disable integrated Web Server.
  Info: Use /trade to view the web-server
web-server-enabled: true

  Port for integrated Web Server (If enabled)
  Make sure to port-forward and disable firewalls for this port.
port: 8123

  The maximum length in data points that the trade-short.html will show (this doesn't affect data)
  Info: When the time-period is set to 10, 144 is one day.
maximum-short-trade-length: 144

  Server name that will show up in commands and requests
server-name: 'My Server'

  Time Period in minutes
  Info: This should be around a tenth of the total items in your shop (i.e with 150 items this would be 15) to prevent overload
  Info: When decreasing or increasing this adjust your volatility settings accordingly
time-period: 5

  The amount of menu rows in the GUI shop, value of 4-6.
menu-rows: 6

  GUI Shop Menu title
menu-title: 'Shop'

  Message sent for players with no permission
no-permission: '&eYou do not have permission to perform this command'

Enable player-automatic-selling
auto-sell-enabled: true 

  How often auto-sell updates in ticks
  Info: Set this higher if few players use autosell
  Info: Set it lower if many players use autosell
auto-sell-update-period: 25

  How often players are shown their auto-sell profits in ticks
auto-sell-profit-update-period: 1200

  -- Pricing Model Settings --  

  Percentage difference in sell price to buy price
sell-price-difference: 10.0

  Maximum Volatility per Time Period for the Variable Volatility price calculation algorithim as a percentage of total price
max-volatility: 0.5

  Minimum Volatility per Time Period for the Fixed Volatility price calculation algorithim in economy units
min-volatility: 0.025

  Send players data about the most significant changes in the economy when they join
send-player-top-movers-on-join: true

  How many items should be displayed for sell + buy (A value of 5 means 10 items as 5 for sell + buy)
top-movers-amount: 5

  -- Data Selection Settings --  

  Info: When setting your data selection algorithim use a site such as https://www.desmos.com/calculator
  Info: Data selection uses the equation y=m(x^z)+c, for example the default is y=0.075(x^1.6)+1.25

  'm' in equation: y=m(x^z)+c
data-selection-m: 0.05

  'z' in equation: y=m(x^z)+c
data-selection-z: 1.6

  'c' in equation: y=m(x^z)+c
data-selection-c: 1.05

  -- Other Econonomy Settings --

  Update prices for server when no players are online
  Info: It is recommended to keep this to false
update-prices-when-inactive: false

  The symbol that appears before all currency
currency-symbol: '$'

  Enable sell price difference variation to ease out sell price variation
sell-price-difference-variation-enabled: true

  Starting percententage sell price difference for sell price variation
sell-price-difference-variation-start: 22.5

  Time in minutes until sell price reaches sell-price-difference set in pricing model settings (default 7 days)
sell-price-variation-time-period: 10080

  Time in minutes that the sell-price-difference updates
  Info: Must be a factor of sell-price-variation-time-period or it won't work!
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
dynamic-inflation-value: 0.00025

  Percentage increase for buy value per price calculation update period.
static-inflation-value: 0.1

  Intrest rate per interest-rate-update-period
  Info: This is the increase in the current debt payment per-time period
interest-rate: 0.005

  Intrest rate for compound-intrest loans
  Info: Compound interest loans grow faster so should have a lower initial interest rate
compound-interest-rate: 0.0025

  Time period in ticks between updates of the interest rate for users loans
interest-rate-update-period: 1200

  lowest value in $ a player can go into debt
  Example: -10.00
max-debt-value: -10000.00

  The percentage value to decrease items sold with enchantments
  Info: Stacked enchantments etc. can become very expensive so a number between 5% - 15% is usually fine
  Info: This doesn't affect buys
enchantment-limiter: 7.50

  The percentage value to decrease items sold with a loss in durability
  Info: This is applied ON TOP of the durability algorithm to limit the exploitability of selling tools
durability-limiter: 5.00

  -- Other Settings --

  Enable debug mode for more info on price calculations
debug-enabled: false

  Enable ChecksumHeaderBypass if you have issues with data retrieval or corruption
checksum-header-bypass: true

  Enable the Auto-Tune tutorial for players
  Keep this on to encourage purchasing turn it off if it is distracting
tutorial: true

  Time in seconds between messages
tutorial-message-period: 325
  </details>

  #### Configuring shops.yml
  
  <details>
  <summary>Shops.yml</summary>
    
   Auto-Tune's Shops.yml file:

      
 ░█████╗░██╗░░░██╗████████╗░█████╗░░░░░░░████████╗██╗░░░██╗███╗░░██╗███████╗  
 ██╔══██╗██║░░░██║╚══██╔══╝██╔══██╗░░░░░░╚══██╔══╝██║░░░██║████╗░██║██╔════╝  
 ███████║██║░░░██║░░░██║░░░██║░░██║█████╗░░░██║░░░██║░░░██║██╔██╗██║█████╗░░  
 ██╔══██║██║░░░██║░░░██║░░░██║░░██║╚════╝░░░██║░░░██║░░░██║██║╚████║██╔══╝░░  
 ██║░░██║╚██████╔╝░░░██║░░░╚█████╔╝░░░░░░░░░██║░░░╚██████╔╝██║░╚███║███████╗  
 ╚═╝░░╚═╝░╚═════╝░░░░╚═╝░░░░╚════╝░░░░░░░░░░╚═╝░░░░╚═════╝░╚═╝░░╚══╝╚══════╝  

 ░██████╗██╗░░██╗░█████╗░██████╗░░██████╗
 ██╔════╝██║░░██║██╔══██╗██╔══██╗██╔════╝
 ╚█████╗░███████║██║░░██║██████╔╝╚█████╗░
 ░╚═══██╗██╔══██║██║░░██║██╔═══╝░░╚═══██╗
 ██████╔╝██║░░██║╚█████╔╝██║░░░░░██████╔╝
 ╚═════╝░╚═╝░░╚═╝░╚════╝░╚═╝░░░░░╚═════╝░


  -- Auto-Tune Default Shops File --  

  Set the default / starting prices for the items you want available in the shop
  Info: Make sure you put a decimal point and two digits to create a double for the shop algorithim to accept and parse to a price-value
  Info: Most of these values will be almost useless once the economy has started, to use these values again delete the data.db file
  Info: Material names are available here: https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Material.html
  Options: 'price: <double>' set the defualt starting price of the item
  Options: 'locked: <boolean>' set the price to be locked or variable (variable by default).
  Options: 'sell-difference: <double>' set a custom sell-price-difference (default set in config).
  Options: 'max-buy': <integer> set a maximum quantity that can be purchased in a time-period
  Options: 'max-sell': <integer> set a maximum quantity that can be sold in a time-period


    Example:
    GRASS_BLOCK:
      price: 50.00
      locked: true      <- Example of a locked item
      sell-difference: 0.0    <- Example of a set sell-difference item
      max-buy: 10       <- Only 10 items can be bought each time-period - you can increase this on large servers
      max-sell: 12       <- Only 12 items can be sold each time-period - you can increase this on large servers

shops:
  COAL:
    price: 15.00
    max-buy: 80
    max-sell: 115
    section: 'Ores'
  COAL_ORE:
    price: 30.00
    max-buy: 50
    max-sell: 90
    section: 'Ores'
  IRON_INGOT:
    price: 13.00
    max-buy: 75
    max-sell: 100
    section: 'Ores'
  IRON_ORE:
    price: 15.00
    max-buy: 60
    max-sell: 100
    section: 'Ores'
  GOLD_INGOT:
    price: 30.00
    max-buy: 45
    max-sell: 75
    section: 'Ores'
  GOLD_ORE:
    price: 32.50
    max-buy: 40
    max-sell: 90
    section: 'Ores'
  DIAMOND:
    price: 300.00
    max-buy: 12
    max-sell: 32
    section: 'Ores'
  DIAMOND_ORE:
    price: 550.00
    max-buy: 5
    max-sell: 12
    section: 'Ores'
  LAPIS_LAZULI:
    price: 18.00
    max-buy: 128
    max-sell: 256
    section: 'Ores'
  LAPIS_ORE:
    price: 35.00
    max-buy: 64
    max-sell: 80
    section: 'Ores'
  EMERALD:
    price: 150.00
    max-buy: 12
    max-sell: 32
    section: 'Ores'
  EMERALD_ORE:
    price: 250.00
    max-buy: 6
    max-sell: 32
    section: 'Ores'
  QUARTZ:
    price: 17.00
    max-buy: 40
    max-sell: 125
    section: 'Ores'
  NETHER_QUARTZ_ORE:
    price: 40.00
    max-buy: 75
    max-sell: 90
    section: 'Ores'
  REDSTONE:
    price: 9.00
    max-buy: 80
    max-sell: 128
    section: 'Ores'
  REDSTONE_ORE:
    price: 25.00
    max-buy: 50
    max-sell: 80
    section: 'Ores'
  NETHERITE_INGOT:
    price: 8000.00
    max-buy: 1
    max-sell: 2
    section: 'Ores'
  NETHERITE_SCRAP:
    price: 3000.00
    max-buy: 2
    max-sell: 4
    section: 'Ores'
  GLOWSTONE_DUST:
    price: 5.00
    max-buy: 128
    max-sell: 256
    section: 'Ores'
  DIRT:
    price: 0.5
    max-buy: 512
    max-sell: 1024
    section: 'Blocks'
  GRASS_BLOCK:
    price: 4.00
    max-buy: 128
    max-sell: 256
    section: 'Blocks'
  MYCELIUM:
    price: 8.00
    max-buy: 75
    max-sell: 100
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
  GRANITE:
    price: 0.80
    max-buy: 128
    max-sell: 512
    section: 'Blocks'
  DIORITE:
    price: 0.80
    max-buy: 128
    max-sell: 512
    section: 'Blocks'
  ANDESITE:
    price: 1.00
    max-buy: 128
    max-sell: 512
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
  GLASS:
    price: 3.00
    max-buy: 90
    max-sell: 200
    section: 'Blocks'
  SNOWBALL:
    price: 1.50
    max-buy: 128
    max-sell: 512
    section: 'Blocks'
  CLAY_BALL:
    price: 2.50
    max-buy: 64
    max-sell: 128
    section: 'Blocks'
  ICE:
    price: 5.50
    max-buy: 128
    max-sell: 256
    section: 'Blocks'
  PRISMARINE:
    price: 9.00
    max-buy: 64
    max-sell: 175
    section: 'Blocks'
  PRISMARINE_BRICKS:
    price: 10.00
    max-buy: 64
    max-sell: 175
    section: 'Blocks'
  DARK_PRISMARINE:  
    price: 12.50
    max-buy: 64
    max-sell: 175
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
  NETHER_BRICKS:
    price: 8.00
    max-buy: 64
    max-sell: 128
    section: 'Blocks'
  BLACKSTONE:
    price: 2.25
    max-buy: 64
    max-sell: 100
    section: 'Blocks'
  SOUL_SAND:
    price: 4.00
    max-buy: 64
    max-sell: 175
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
  SPRUCE_LOG:
    price: 8.50
    max-buy: 90
    max-sell: 256
    section: 'Blocks'
  BIRCH_LOG:
    price: 8.50
    max-buy: 90
    max-sell: 256
    section: 'Blocks'
  DARK_OAK_LOG:
    price: 8.75
    max-buy: 90
    max-sell: 256
    section: 'Blocks'
  ACACIA_LOG:
    price: 8.25
    max-buy: 90
    max-sell: 256
    section: 'Blocks'
  JUNGLE_LOG:
    price: 8.25
    max-buy: 90
    max-sell: 256
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
    section: 'Farming'
  EGG:
    price: 3.50
    max-buy: 32
    max-sell: 128
    section: 'Farming'
  MELON_SLICE:
    price: 1.75
    max-buy: 85
    max-sell: 175
    section: 'Farming'
  APPLE:
    price: 4.00
    max-buy: 64
    max-sell: 100
    section: 'Farming'
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
    section: 'Farming'
  PORKCHOP:
    price: 2.25
    max-buy: 64
    max-sell: 150
    section: 'Farming'
  CHICKEN:
    price: 1.45
    max-buy: 64
    max-sell: 150
    section: 'Farming'
  RABBIT:
    price: 2.50
    max-buy: 64
    max-sell: 150
    section: 'Farming'
  MUTTON:
    price: 2.00
    max-buy: 64
    max-sell: 150
    section: 'Farming'
  PUMPKIN:
    price: 8.00
    max-buy: 64
    max-sell: 128
    section: 'Farming'
  RED_MUSHROOM:
    price: 6.00
    max-buy: 64
    max-sell: 100
    section: 'Farming'
  BROWN_MUSHROOM:
    price: 6.00
    max-buy: 64
    max-sell: 100
    section: 'Farming'
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
    section: 'Materials'
  GUNPOWDER:
    price: 12.50
    max-buy: 64
    max-sell: 128
    section: 'Materials'
  LEATHER:
    price: 20.00
    max-buy: 64
    max-sell: 128
    section: 'Materials'
  INK_SAC:
    price: 2.50
    max-buy: 32
    max-sell: 64
    section: 'Materials'
  FEATHER:
    price: 4.00
    max-buy: 32
    max-sell: 64
    section: 'Materials'
  BONE:
    price: 6.00
    max-buy: 64
    max-sell: 100
    section: 'Materials'
  BONE_MEAL:
    price: 2.00
    max-buy: 64
    max-sell: 90
    section: 'Materials'
  STRING:
    price: 4.00
    max-buy: 64
    max-sell: 100
    section: 'Materials'
  ROTTEN_FLESH:
    price: 0.50
    max-buy: 64
    max-sell: 128
    section: 'Materials'
  SPIDER_EYE:
    price: 7.00
    max-buy: 64
    max-sell: 128
    section: 'Materials'
  SLIME_BALL:
    price: 25.00
    max-buy: 32
    max-sell: 64
    section: 'Materials'
  ENDER_PEARL:
    price: 35.00
    max-buy: 16
    max-sell: 40
    section: 'Materials'
  NETHER_STAR:
    price: 12000.00
    max-buy: 1
    max-sell: 1
    section: 'Materials'
  GHAST_TEAR:
    price: 125.00
    max-buy: 4
    max-sell: 8
    section: 'Materials'
  MAGMA_CREAM:
    price: 50.00
    max-buy: 16
    max-sell: 32
    section: 'Materials'
  BLAZE_ROD:
    price: 45.00
    max-buy: 8
    max-sell: 32
    section: 'Materials'
  ARROW:
    price: 2.00
    max-buy: 64
    max-sell: 128
    section: 'Materials'
  NAME_TAG:
    price: 50.00
    max-buy: 8
    max-sell: 16
    section: 'Materials'
  CHORUS_FRUIT:
    price: 1.25
    max-buy: 64
    max-sell: 128
    section: 'Farming'
  CHEST:
    price: 20.00
    max-buy: 30
    max-sell: 64
    section: 'Blocks'
  TORCH:
    price: 2.5
    max-buy: 64
    max-sell: 100
    section: 'Blocks'
  NOTE_BLOCK:
    price: 36.0
    max-buy: 16
    max-sell: 32
    section: 'Blocks'
  BUCKET:
    price: 7.50
    max-buy: 16
    max-sell: 32
    section: 'Other'
  FURNACE:
    price: 8.5
    max-buy: 32
    max-sell: 48
    section: 'Blocks'
  TNT:
    price: 125.0
    max-buy: 16
    max-sell: 32
    section: 'Blocks'
  SPONGE:
    price: 60.00
    max-buy: 32
    max-sell: 128
    section: 'Blocks'
  PHANTOM_MEMBRANE:
    price: 45.00
    max-buy: 32
    max-sell: 64
    section: 'Materials'
  OAK_LEAVES:
    price: 1.0
    max-buy: 64
    max-sell: 128
    section: 'Blocks'
  BIRCH_LEAVES:
    price: 1.0
    max-buy: 64
    max-sell: 128
    section: 'Blocks'
  SPRUCE_LEAVES:
    price: 1.0
    max-buy: 64
    max-sell: 128
    section: 'Blocks'
  JUNGLE_LEAVES:
    price: 1.0
    max-buy: 64
    max-sell: 128
    section: 'Blocks'
  DARK_OAK_LEAVES:
    price: 1.0
    max-buy: 64
    max-sell: 128
    section: 'Blocks'
  ACACIA_LEAVES:
    price: 1.0
    max-buy: 64
    max-sell: 128
    section: 'Blocks'
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
    section: 'Materials'
  BIRCH_SAPLING:
    price: 2.5
    max-buy: 32
    max-sell: 64
    section: 'Materials'
  SPRUCE_SAPLING:
    price: 2.5
    max-buy: 32
    max-sell: 64
    section: 'Materials'
  JUNGLE_SAPLING:
    price: 2.5
    max-buy: 32
    max-sell: 64
    section: 'Materials'
  DARK_OAK_SAPLING:
    price: 2.5
    max-buy: 32
    max-sell: 64
    section: 'Materials'
  ACACIA_SAPLING:
    price: 2.5
    max-buy: 32
    max-sell: 64
    section: 'Materials'
  DISPENSER:
    price: 58.0
    max-buy: 8
    max-sell: 16
    section: 'Blocks'
  CAKE:
    price: 160.0
    max-buy: 8
    max-sell: 10
    section: 'Other'
  MILK_BUCKET:
    price: 9.00
    max-buy: 12
    max-sell: 20
    section: 'Other'
  STICK:
    price: 0.2
    max-buy: 64
    max-sell: 128
    section: 'Materials'
  BOOK:
    price: 28.0
    max-buy: 32
    max-sell: 48
    section: 'Other'
  Shop sections configuration
sections:
  'Ores':
    block: 'IRON_ORE'
  'Blocks':
    block: 'STONE'
  'Materials':
    block: 'STICK'
  'Farming':
    block: 'WHEAT_SEEDS'
  'Other':
    block: 'COMMAND_BLOCK'
 </details>

## Auto-Tune-API

  #### What is Auto-Tune API

Auto-Tune API is the API Auto-Tune connects to, in order to automatically calculate price data. In other words, it is the server that is the backbone of the dynamic pricing-model that powers Auto-Tune.
    
  #### How to get an Auto-Tune API key

To get an Auto-Tune API key please open a ticket on our discord:

[![Discord](https://img.shields.io/discord/748222485975269508.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/bNVVPe5)

