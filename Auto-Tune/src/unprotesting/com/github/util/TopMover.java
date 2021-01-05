package unprotesting.com.github.util;

import java.util.Collections;
import java.util.List;

import unprotesting.com.github.Main;
import unprotesting.com.github.Commands.AutoTuneGUIShopUserCommand;

public class TopMover implements Comparable< TopMover >{

    public double price;
    public double percentage_change = 0.0;
    public String name;

    public TopMover(String name){
        this.name = name;
        this.price = AutoTuneGUIShopUserCommand.getItemPrice(name, false);
        this.percentage_change = loadPercentageChange(this);
        if (this.percentage_change != 0.0){
            if (this.percentage_change > 0){
                if (Main.getTopBuyers().size() < Config.getTopMoversAmount()){
                    Main.topBuyers.add(this);
                }
                else{
                    Collections.sort(Main.topBuyers);
                    if (Main.getTopBuyers().get(Config.getTopMoversAmount()-1).percentage_change < this.percentage_change){
                        Main.topBuyers.remove(Config.getTopMoversAmount()-1);
                        Main.topBuyers.add(Config.getTopMoversAmount()-1, this);
                    }
                }
            }
            if (this.percentage_change < 0){
                if (Main.getTopSellers().size() < Config.getTopMoversAmount()){
                    Main.topSellers.add(this);
                }
                else{
                    Collections.sort(Main.topSellers);
                    if (Main.getTopSellers().get(Config.getTopMoversAmount()-1).percentage_change > this.percentage_change){
                        Main.topSellers.remove(Config.getTopMoversAmount()-1);
                        Main.topSellers.add(Config.getTopMoversAmount()-1, this);
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
        List<Double> newMap;
        try{
            newMap = Main.getItemPrices().get(item).prices;
        }
        catch(NullPointerException e){
            try{
                PriceCalculationHandler.loadItemPriceData();
                newMap = Main.getItemPrices().get(item).prices;
            }
            catch(NullPointerException ex){
                return 0.0;
            }
        }
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

    @Override
    public int compareTo(TopMover o) {
        if (o.percentage_change < 0 && this.percentage_change < 0){
            return (int) (this.percentage_change*1000 - o.percentage_change*1000);
        }
        else{
            return (int) (o.percentage_change*1000 - this.percentage_change*1000);
        }
    }
    
}
