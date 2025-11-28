package de.dktutzer.tcgwatcher.data.data.entities;

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

  // internal id
  @Id()
  @Include
  @Column(columnDefinition = "TEXT")
  private String id;

  // the different names of the card in various languages
  @Column(name = "name_de", nullable = false, columnDefinition = "TEXT")
  private String nameDe;
  @Column(name = "name_fr", nullable = false, columnDefinition = "TEXT")
  private String nameFr;
  @Column(name = "name_en", nullable = false, columnDefinition = "TEXT")
  private String nameEn;

  // the card code
  // eg: MEG 104 for Mega-Kangaskhan-ex-V1-MEG104
  @Column(nullable = false, columnDefinition = "TEXT")
  private String code;

  // eg: 5347623473
  @Column(nullable = false, columnDefinition = "TEXT")
  private String cmProductId;

  // eg: Mega-Kangaskhan-ex-V1-MEG104
  @Column(nullable = false, columnDefinition = "TEXT")
  private String cmPageId;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String tcgpId;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String setId;

}
