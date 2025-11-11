package de.dktutzer.tcgwatcher.data.data.model;

import java.util.Map;

public record DexSetData(
    String id,
    Map<String,String> name,
    String series,
    Integer printedTotal,
    Integer total,
    String releaseDate,
    String ptcgoCode,
    Map<String,String> abbreviations,
    Map<String,String> thirdParty,
    Map<String,String> images,
    Map<String, DexCardData> cards) {
}
