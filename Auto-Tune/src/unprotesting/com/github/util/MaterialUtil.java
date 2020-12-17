package unprotesting.com.github.util;

import org.bukkit.Material;

public class MaterialUtil {

    public static final Material[] LOGS, WOOL, SAPLINGS, WOOD, LEAVES;

    static Material[] values = Material.values();

    static{
    LOGS = matchMaterials("OAK_LOG", "BIRCH_LOG", "SPRUCE_LOG", "JUNGLE_LOG", "DARK_OAK_LOG", "ACACIA_LOG");
    WOOL = matchMaterials("WHITE_WOOL", "ORANGE_WOOL", "MAGENTA_WOOL", "LIGHT_BLUE_WOOL", "YELLOW_WOOL", "LIME_WOOL", "PINK_WOOL",
    "GRAY_WOOL", "LIGHT_GRAY_WOOL", "CYAN_WOOL", "PURPLE_WOOL", "BLUE_WOOL", "BROWN_WOOL", "GREEN_WOOL", "RED_WOOL", "BLACK_WOOL");
    SAPLINGS = matchMaterials("OAK_SAPLING", "BIRCH_SAPLING", "SPRUCE_SAPLING", "JUNGLE_SAPLING", "DARK_OAK_SAPLING", "ACACIA_SAPLING");
    WOOD = matchMaterials("OAK_PLANKS", "BIRCH_PLANKS", "SPRUCE_PLANKS", "JUNGLE_PLANKS", "DARK_OAK_PLANKS", "ACACIA_PLANKS");
    LEAVES = matchMaterials("OAK_LEAVES", "BIRCH_LEAVES", "SPRUCE_LEAVES", "JUNGLE_LEAVES", "DARK_OAK_LEAVES", "ACACIA_LEAVES");
    }
    
    public static Material[] matchMaterials(final String... names) {
        Material[] output = new Material[names.length];
        int i = 0;
        for (String name : names) {
            output[i] = (Material.matchMaterial(name));
            i++;
        }
        return output;
    }

    public static Material[] MatchInputToSet(String input) {
        input = input.toLowerCase();
        if (input.equals("air") || input.equals("water") || input.equals("lava")){
            return null;
        }
        for (Material mat : values) {
            String str = mat.toString();
            str = (str.replace("_", "")).toLowerCase();
            if (input.equals(str)){
                return new Material[] {mat};
            }
        }
        Material output = Material.matchMaterial((input.toUpperCase()));
        if (output != null){
            return new Material[] {output};
        }
        else {
            Material[] out = MatchInputAndIDToSet(input, "*");
            if (out == null){
                return null;
            }
            else{
                return out;
            }
        }
    }

    public static Material[] MatchInputAndIDToSet(String input, String id){
        if (input.equals("wool")){
            if (id.equals("*")){
                return WOOL;
            }
            else{
                Material output = WOOL[Integer.parseInt(id)];
                Material[] outputSet = {output};
                return outputSet;
            }
        }
        else if (input.equals("log")){
            if (id.equals("*")){
                return LOGS;
            }
            else{
                Material output = LOGS[Integer.parseInt(id)];
                Material[] outputSet = {output};
                return outputSet;
            }
        }
        else if (input.equals("sapling")){
            if (id.equals("*")){
                return SAPLINGS;
            }
            else{
                Material output = SAPLINGS[Integer.parseInt(id)];
                Material[] outputSet = {output};
                return outputSet;
            }
        }
        else if (input.equals("wood")){
            if (id.equals("*")){
                return WOOD;
            }
            else{
                Material output = WOOD[Integer.parseInt(id)];
                Material[] outputSet = {output};
                return outputSet;
            }
        }
        else if (input.equals("leaves")){
            if (id.equals("*")){
                return LEAVES;
            }
            else{
                Material output = LEAVES[Integer.parseInt(id)];
                Material[] outputSet = {output};
                return outputSet;
            }
        }
        else{
            return null;
        }
    }
}

    
