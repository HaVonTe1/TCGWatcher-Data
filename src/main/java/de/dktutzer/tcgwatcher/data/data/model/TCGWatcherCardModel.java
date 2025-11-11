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
  private String setId;

  private String number; //the official card number

  private String cmProductId; //Cardmarket product id
  private String tcgpId; //TCGPlayer id
  private String cmCode; //cardmarket code


}
