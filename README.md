# Auto-Tune

![GitHub Workflow Status](https://img.shields.io/github/workflow/status/Unprotesting/Auto-Tune/Java%20CI%20with%20Maven)
![GitHub issues](https://img.shields.io/github/issues/Unprotesting/Auto-Tune)
![GitHub pull requests](https://img.shields.io/github/issues-pr/Unprotesting/Auto-Tune)
![GitHub release (latest by date)](https://img.shields.io/github/v/release/Unprotesting/Auto-Tune)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/2f6d82bd12af4ce490959be74d1b6149)](https://app.codacy.com/gh/Unprotesting/Auto-Tune?utm_source=github.com&utm_medium=referral&utm_content=Unprotesting/Auto-Tune&utm_campaign=Badge_Grade_Settings)
[![Discord](https://img.shields.io/discord/748222485975269508.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/bNVVPe5)

>A Powerful Minecraft Automatic-Economy Plugin for ```1.13-1.19``` with many features!
<img src="https://github.com/Unprotesting/Auto-Tune/blob/master/.github/AtLogo.png?raw=true" width="100"/>

## :star: Overview

*Auto-Tune is a Minecraft plugin that allows you to create an automated economy for your server. Prices of items will be automatically adjusted based on supply and demand. When an item is purchased by many players but sold by few, Auto-Tune will raise the price to lower demand/increase supply and vice versa. Auto-Tune fixes a critical problem in Minecraft server economies and provides a better experience for players and servers. You can check out our feature set below.*

## :heavy_check_mark: Features

- :ballot_box_with_check: ```Advanced automatic pricing model based on supply and demand```
- :ballot_box_with_check: ```Configurable GUI shop, with positioning and naming options```
- :ballot_box_with_check: ```Easy selling panel to sell items quickly```
- :ballot_box_with_check: ```Automatic selling, configurable player side```
- :ballot_box_with_check: ```Fully supports all enchantments and items```
- :ballot_box_with_check: ```Stored detailed history of transactions```
- :ballot_box_with_check: ```Exploit protection with max-buys/sells, volatility settings, and more```
- :ballot_box_with_check: ```Limit player's to only be able to purchase items they have collected before```
- :ballot_box_with_check: ```Advanced loaning with interest settings```
- :ballot_box_with_check: ```Integrated web-server for viewing price information```
- :ballot_box_with_check: ```Calculates GDP, debt, inflation and more```
- :ballot_box_with_check: ```Incredibly fast data collection and creation with corruption protection```
- :ballot_box_with_check: ```All messages are configurable```
- :ballot_box_with_check: ```Tutorial to help new players```
- :ballot_box_with_check: ```And much, much more...```

## :question: Why use Auto-Tune

Auto-Tune identifies and fixes a significant problem in Minecraft servers that has remained underdeveloped and ignored for too long. This issue is the poor implementation of an economy and markets into Minecraft.

Previous solutions that allow for trading between players have lacked flexibility, player engagement, and realism. These issues are due to server economy plugins that cannot adapt to the speed at which the economy in Minecraft changes. We designed Auto-Tune with this in mind. By automating the price-setting process, server admins can sit back and watch the prices fluctuate as the supply and demand of items bounce back and forth.

Not only does this assist administrators in managing a server's economy, but it also allows the server players to engage with the economy more rigorously. We have strenuously tested Auto-Tune to be fit for any environment and created systems designed to assist server admins in building the best economy possible for the specific needs of their server. Auto-Tune is a powerful and highly customizable plugin that has a feature set rich enough to satisfy any server. We at Auto-Tune are passionate and optimistic about Minecraft plugin development and building a community that loves Minecraft and economics!

## 🎀 Examples

### Auto-Tune Default Shop Setup

<img src="https://github.com/Unprotesting/Auto-Tune/blob/master/.github/Auto-Tune-Shop.gif?raw=true" width="500"/>

## :computer: Usage

### :clipboard: Server setup

1. Download the latest version of Auto-Tune from the [releases](https://github.com/Unprotesting/Auto-Tune/releases) tab on Github. Development versions can be found under the [actions](https://github.com/Unprotesting/Auto-Tune/actions) tab on Github (where each commit produces a build artifact which is the latest version of the plugin).
2. Please use [Paper](https://papermc.io/) or a fork of Paper as your server software.
3. Make sure the required dependencies are installed ([Vault](https://www.spigotmc.org/resources/vault.34315/) and an economy plugin such as [Essentials](https://essentialsx.net))
4. Put the ```.jar``` files in the ```/plugins``` folder of your server.
5. Start/restart the server.
6. Edit your configuration settings in ```config.yml```, ```shops.yml``` and ```messages.yml```.
7. Restart the server again and Auto-Tune will be running with all your settings configured.

### :hammer: Building from source

1. Clone the project to a local directory using ```git clone https://github.com/Unprotesting/Auto-Tune.git```.
2. Run ```cd Auto-Tune``` to enter the Auto-Tune folder.
3. Run ```./gradlew build``` to build the project using Gradle.
4. Navigate to the ```/builds/libs/``` directory and ```Auto-Tune-0.x.x``` will be there if the build was successful.

### :sparkles: Contributing to the project

Feel free to create a fork of the repository and open a pull request to contribute. If you have any serious issues please report them on the issues tab. For other problems please use the discord below. Please respect the license.

## :bell: Join the community

> [![Discord](https://img.shields.io/discord/748222485975269508.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/bNVVPe5)
>
> :email: *unprotesting.email@gmail.com*
>
> :calling: **Unprotesting#3616**
