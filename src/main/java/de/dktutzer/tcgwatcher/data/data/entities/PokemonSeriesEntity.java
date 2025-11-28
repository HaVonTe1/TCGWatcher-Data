package de.dktutzer.tcgwatcher.data.data.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Entity(name = "PokemonSeries")
@Table(name = "qs_pokemon_series")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class PokemonSeriesEntity {

  @Id
  @EqualsAndHashCode.Include
  @Column(columnDefinition = "TEXT")
  private String id;

  @Column(name = "name_de", nullable = false, columnDefinition = "TEXT")
  private String nameDe;
  @Column(name = "name_fr", nullable = false, columnDefinition = "TEXT")
  private String nameFr;
  @Column(name = "name_en", nullable = false, columnDefinition = "TEXT")
  private String nameEn;
}
