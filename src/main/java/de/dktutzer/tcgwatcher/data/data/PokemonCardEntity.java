package de.dktutzer.tcgwatcher.data.data;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.EqualsAndHashCode.Include;
import lombok.Getter;
import lombok.Setter;

@Entity(name = "PokemonCard")
@Table(name = "qs_pokemon_cards")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class PokemonCardEntity {


  @Id()
  @Include
  @Column(columnDefinition = "INTEGER")
  private Long id;

  @Column(name = "name_de", nullable = false, columnDefinition = "TEXT")
  private String nameDe;
  @Column(name = "name_fr", nullable = false, columnDefinition = "TEXT")
  private String nameFr;
  @Column(name = "name_en", nullable = false, columnDefinition = "TEXT")
  private String nameEn;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String code;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String externalId;


}
