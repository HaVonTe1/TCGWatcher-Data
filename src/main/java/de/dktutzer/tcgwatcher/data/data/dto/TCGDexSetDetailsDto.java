package de.dktutzer.tcgwatcher.data.data.dto;

import lombok.Data;

@Data
public class TCGDexSetDetailsDto {

  private String id;
  private String name;
  private CardCount cardCount;
  private Series serie;
  private Abbreviation abbreviation;

  @Data
  public static class Series {
    private String id;
    private String name;
  }

  @Data
  public static class CardCount {
    private Integer official;
    private Integer total;
  }

  @Data
  public static class Abbreviation {
    private String official;
  }


}
