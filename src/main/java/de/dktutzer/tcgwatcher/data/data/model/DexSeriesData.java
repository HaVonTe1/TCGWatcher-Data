package de.dktutzer.tcgwatcher.data.data.model;

import java.util.Map;

public record DexSeriesData(
    String id,
    Map<String,String> name,
    Map<String, DexSetData> sets) {
}
