package de.dktutzer.tcgwatcher.data.service;

import de.dktutzer.tcgwatcher.data.data.PokemonCardEntity;
import org.springframework.data.repository.CrudRepository;

public interface QuickSearchCardsSqliteRepository extends CrudRepository<PokemonCardEntity, Long> {


}
