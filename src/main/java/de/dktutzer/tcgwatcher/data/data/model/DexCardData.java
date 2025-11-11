package de.dktutzer.tcgwatcher.data.data.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record DexCardData(
    String id,  //internal  id - does not matter at all
    Map<String,String> names,  // eg. <de, "Arkani>, <em, Arcanine> etc
    String number, //1
    String supertype,
    List<String> subtypes,
    String rarity,
    String hp,
    List<String> types,
    Map<String,String> evolveFrom,
    String stage,
    List<Map<String,String>> abilities,
    List<Map<String,String>> attacks,
    List<Map<String, String>> weaknesses,
    List<Map<String, String>> resistances,
    List<String> retreatCost,
    Integer retreat,
    Integer convertedRetreatCost,
    String artist,
    Map<String,String> description,
    Map<String,String> thirdParty,  //<cardmarket, 278973>, <tcgplayer, 83586>
    List<Integer> dexId, //dexId
    Path sourceFile) {
}
