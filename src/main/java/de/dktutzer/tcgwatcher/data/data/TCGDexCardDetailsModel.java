package de.dktutzer.tcgwatcher.data.data;

import lombok.Data;

@Data

public class TCGDexCardDetailsModel {
  private String id;
  private String localId;
  private String name;
  private SetInfo set;


  @Data
  public static class SetInfo {
    private CardCount cardCount;
    private String id;
    private String logo;
    private String name;
  }
  @Data
  public static class CardCount {
    private int official;
    private int total;
  }

}
