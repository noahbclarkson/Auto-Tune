package unprotesting.com.github.data.ephemeral.other;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import unprotesting.com.github.data.ephemeral.other.Sale.SalePositionType;

//  Player sale data class for storing general sale data

public class PlayerSaleData {

    @Getter
    private List<Sale> buys,
                       sells, 
                       ebuys, 
                       esells;

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
                this.buys.add(sale);
                break;
            case SELL:
                this.sells.add(sale);
                break;
            case EBUY:
                this.ebuys.add(sale);
                break;
            case ESELL:
                this.esells.add(sale);
                break;
            default:
                break;
        }
    }

    //  Ensure a sale object for an item doesn't already exist in a select list
    private Sale checkIfSaleExisits(String item, SalePositionType position){
        switch(position){
            case BUY:
                Sale sale = getSale(item, this.buys);
                this.buys.remove(sale);
                return sale;
            case SELL:  
                Sale sale2 = getSale(item, this.sells);
                this.sells.remove(sale2);
                return sale2;
            case EBUY:
                Sale sale3 = getSale(item, this.ebuys);
                this.ebuys.remove(sale3);
                return sale3;
            case ESELL:
                Sale sale4 = getSale(item, this.esells);
                this.esells.remove(sale4);
                return sale4;
            default:
                return new Sale(item, 0);
        }
    }

    private Sale getSale(String item, List<Sale> sales){
        for (Sale sale : sales){
            if(sale.getItem().equals(item)){
                return sale;
            }
        }
        return new Sale(item, 0);
    }
    
}
