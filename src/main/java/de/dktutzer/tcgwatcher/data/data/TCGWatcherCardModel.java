package de.dktutzer.tcgwatcher.data.data;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TCGWatcherCardModel {

  private String id;
  private Map<String, String> names;
  private String setId;
  private String number;
  private String cmSetId;
  private String cmCardId;

}
