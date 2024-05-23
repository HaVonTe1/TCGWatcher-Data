package de.dktutzer.tcgwatcher.data.data;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.EqualsAndHashCode.Include;
import lombok.Getter;
import lombok.Setter;

@Entity(name = "PokemonCard")
@Table(name = "quicksearch_cards", indexes = @Index(name = "namesIdx", columnList = "name_de, name_en, name_fr"))
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class PokemonCardEntity {


  @Id()
  @Include
  private Long id;

  @Column(name = "name_de")
  private String nameDe;
  @Column(name = "name_fr")
  private String nameFr;
  @Column(name = "name_en")
  private String nameEn;


}
