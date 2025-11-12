package de.dktutzer.tcgwatcher.data.data.model;

import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TCGWatcherSetModel {

  private String id;
  private Integer numberTotal;
  private Integer numberOfficial;
  private String code;
  private String abbreviation;
  private Map<String, String> names;
  private TCGWatcherSeriesModel series;

  private String releaseDate; //store dates in ISO8601 so sqlite can handle it

  private String cmSetId;
  private String tcgpSetId;

}
