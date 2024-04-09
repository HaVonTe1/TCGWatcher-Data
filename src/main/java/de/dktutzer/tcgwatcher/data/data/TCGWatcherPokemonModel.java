package de.dktutzer.tcgwatcher.data.data;

import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TCGWatcherPokemonModel {

  private String id;
  private Map<String, String> names;
  private String setId;
  private String number;

}
