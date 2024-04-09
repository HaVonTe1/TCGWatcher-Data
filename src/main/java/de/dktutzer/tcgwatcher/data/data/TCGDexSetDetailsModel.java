package de.dktutzer.tcgwatcher.data.data;

import de.dktutzer.tcgwatcher.data.data.TCGDexCardDetailsModel.CardCount;
import lombok.Data;

@Data
public class TCGDexSetDetailsModel {

  private String id;
  private String name;
  private CardCount cardCount;
  private SeriesModel serie;

  @Data
  public static class SeriesModel {
    private String name;
  }

  @Data
  public static class CardCount {
    private Integer official;
  }

}
