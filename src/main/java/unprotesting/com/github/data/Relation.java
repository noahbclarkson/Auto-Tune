package unprotesting.com.github.data;

import lombok.Data;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.tuple.Tuples;

/**
 * A class for storing the relation of prices between shops.
 */
@Data
public class Relation {

    private double relation;
    private Pair<Integer, Integer> buys;
    private Pair<Integer, Integer> sells;

    /**
     * Constructor for a relation class.
     *
     * @param shop  The shop to get the relation from.
     * @param shop2 The shop to get the relation to.
     */
    protected Relation(Shop shop, Shop shop2) {
        this.buys = Tuples.pair(shop.getTotalBuys(), shop2.getTotalBuys());
        this.sells = Tuples.pair(shop.getTotalSells(), shop2.getTotalSells());
        this.relation = shop.getPrice() / shop2.getPrice();
    }

}
