package de.dktutzer.tcgwatcher.data.data.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.EqualsAndHashCode.Include;
import lombok.Getter;
import lombok.Setter;

@Entity(name = "PokemonCardFts")
@Table(name = "qs_fts_pokemon_cards")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class PokemonCardFtsEntity {


  @Id()
  @Include
  @Column(columnDefinition = "TEXT")
  private String id;

  @Column(name = "names", nullable = false, columnDefinition = "TEXT")
  private String names;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String code;

}
