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
public class TCGWatcherSeriesModel {

  private String id;
  private Map<String, String> names;

}
