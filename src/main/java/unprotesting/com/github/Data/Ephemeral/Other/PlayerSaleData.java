package unprotesting.com.github.Data.Ephemeral.Other;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import unprotesting.com.github.Data.Ephemeral.Other.Sale.SalePositionType;

//  Player sale data class for storing general sale data

public class PlayerSaleData {

    @Getter
    private List<Sale> buys, sells, ebuys, esells;

    public PlayerSaleData(){
        this.buys = new ArrayList<Sale>();
        this.sells = new ArrayList<Sale>();
        this.ebuys = new ArrayList<Sale>();
        this.esells = new ArrayList<Sale>();
    }

    //  Add a new sale to one of the sale lists
    public void addSale(String item, int amount, SalePositionType position){
        Sale sale = checkIfSaleExisits(item, position);
        sale.setAmount(sale.getAmount()+amount);
        switch(position){
            case BUY:
                buys.add(sale);
                break;
            case SELL:
                sells.add(sale);
                break;
            case EBUY:
                ebuys.add(sale);
                break;
            case ESELL:
                esells.add(sale);
                break;
            default:
                break;
        }
    }

    //  Ensure a sale object for an item doesn't already exist in a select list
    private Sale checkIfSaleExisits(String item, SalePositionType position){
        switch(position){
            case BUY:
                return getSale(item, this.buys);
            case SELL:  
                return getSale(item, this.sells);
            case EBUY:
                return getSale(item, this.ebuys);
            case ESELL:
                return getSale(item, this.esells);
            default:
                break;
        }
        return new Sale(item, 0);
    }

    private Sale getSale(String item, List<Sale> sales){
        for (Sale sale : sales){
            if(sale.getItem().equals(item)){
                buys.remove(sale);
                return sale;
            }
        }
        return new Sale(item, 0);
    }
    
}
