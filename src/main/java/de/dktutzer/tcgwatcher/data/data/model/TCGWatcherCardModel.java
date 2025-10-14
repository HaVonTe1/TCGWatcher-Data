package de.dktutzer.tcgwatcher.data.data.model;

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
  //the internal set id
  private String setId;
  //the official card number
  private String number;

}
