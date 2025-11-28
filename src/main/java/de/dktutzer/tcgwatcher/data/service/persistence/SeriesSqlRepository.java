package de.dktutzer.tcgwatcher.data.service.persistence;

import de.dktutzer.tcgwatcher.data.data.entities.PokemonSeriesEntity;
import org.springframework.data.repository.CrudRepository;

public interface SeriesSqlRepository extends CrudRepository<PokemonSeriesEntity, String> {}
