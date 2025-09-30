package de.dktutzer.tcgwatcher.data.data.model;

import lombok.Data;

@Data
public class TCGDataSetModel {

  private String id;
  private String name;
  private String series;
  private int printedTotal;
  private int total;
  private String ptcgoCode;
}
