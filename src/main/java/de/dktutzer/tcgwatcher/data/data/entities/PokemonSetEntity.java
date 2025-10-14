package de.dktutzer.tcgwatcher.data.data.entities;

  import jakarta.persistence.Column;
  import jakarta.persistence.Entity;
  import jakarta.persistence.Id;
  import jakarta.persistence.JoinColumn;
  import jakarta.persistence.ManyToOne;
  import jakarta.persistence.Table;
  import lombok.EqualsAndHashCode;
  import lombok.Getter;
  import lombok.Setter;

  @Entity(name = "PokemonSet")
  @Table(name = "qs_pokemon_sets")
  @Getter
  @Setter
  @EqualsAndHashCode(onlyExplicitlyIncluded = true)
  public class PokemonSetEntity {

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

    @Column(nullable = false, columnDefinition = "TEXT")
    private String code;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String abbreviation;

    @Column(nullable = false, columnDefinition = "NUMBER")
    private Integer total;

    @Column(nullable = false, columnDefinition = "NUMBER")
    private Integer official;

    @ManyToOne(optional = false)
    @JoinColumn(name = "series_id", referencedColumnName = "id")
    private PokemonSeriesEntity series;
  }
