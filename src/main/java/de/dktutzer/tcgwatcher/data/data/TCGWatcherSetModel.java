package de.dktutzer.tcgwatcher.data.data;

import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TCGWatcherSetModel {

  private String id;
  private Integer number;
  private String code;
  private Map<String, String> names;
  private Map<String, String> series;

}
