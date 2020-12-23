package unprotesting.com.github.util;

import java.util.List;

import unprotesting.com.github.Main;
import unprotesting.com.github.Commands.AutoTuneGUIShopUserCommand;

public class TopMover {

    public double price;
    public double percentage_change = 0.0;
    public String name;

    public TopMover(String name){
        this.name = name;
        this.price = AutoTuneGUIShopUserCommand.getItemPrice(name, false);
        this.percentage_change = loadPercentageChange(this);
        if (percentage_change != 0.0){
            if (percentage_change > 0){
                int nullValue = 100000;
                for (int k = 0; k < Config.getTopMoversAmount(); k++){
                    if (Main.topBuyers[k] == null){
                        nullValue = k;
                        break;
                    }
                }
                if (nullValue < 99999){
                    Main.topBuyers[nullValue] = this;
                }
                else {
                    double lowest_percentage = 100.00;
                    int pos = 10000;
                    int i = 0;
                    for (TopMover topMover : Main.topBuyers){
                        if (topMover.percentage_change < lowest_percentage){
                            lowest_percentage = topMover.percentage_change;
                            pos = i;
                        }
                        i++;
                    }
                    if (this.percentage_change > lowest_percentage && pos != 10000){
                        Main.topBuyers[pos] = this;
                    }
                }
            }
            else if (percentage_change < 0){
                int nullValue = 100000;
                for (int k = 0; k < Config.getTopMoversAmount(); k++){
                    if (Main.topSellers[k] == null){
                        nullValue = k;
                        break;
                    }
                }
                if (nullValue < 99999){
                    Main.topSellers[nullValue] = this;
                }
                else {
                    double highest_percentage = 100.00;
                    int pos = 10000;
                    int i = 0;
                    for (TopMover topMover : Main.topSellers){
                        if (topMover.percentage_change < highest_percentage){
                            highest_percentage = topMover.percentage_change;
                            pos = i;
                        }
                        i++;
                    }
                    if (this.percentage_change < highest_percentage && pos != 10000){
                        Main.topSellers[pos] = this;
                    }
                }
            }
        }
    }

    public double loadPercentageChange(TopMover mover) {
        String item = mover.name;
        double currentPrice = mover.price;
		float timePeriod = (float) Config.getTimePeriod();
		float timePeriodsInADay = (float) (1 / (timePeriod / 1440));
		List<Double> newMap = Main.getItemPrices().get(item).prices;
		if (newMap.size() <= timePeriodsInADay) {
			return 0.0;
		}
		Integer oneDayOldTP = (int) Math.floor(newMap.size() - timePeriodsInADay);
		double oneDayOldPrice = newMap.get(oneDayOldTP);
		if (oneDayOldPrice > currentPrice) {
			double percent = 100 * ((currentPrice / oneDayOldPrice) - 1);
			return -1*Math.abs(percent);
		} else if (oneDayOldPrice < currentPrice) {
			double percent = 100 * (1 - (oneDayOldPrice / currentPrice));
			return Math.abs(percent);
		} else {
			return 0.0;
		}
	}

    public String toString(){
        return ("Name: " + this.name + " | Price: " + this.price + " | Percentage Change: %" + this.percentage_change);
    }
    
}
