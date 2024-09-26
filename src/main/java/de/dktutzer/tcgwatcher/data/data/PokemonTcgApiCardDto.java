package de.dktutzer.tcgwatcher.data.data;

import lombok.Data;

@Data
public class PokemonTcgApiCardDto {

  private DataDto data;

  @Data
  public static class DataDto {
    private CardmarketDto cardmarket;
  }

  @Data
  public static class CardmarketDto {
    private String url;
  }

}
