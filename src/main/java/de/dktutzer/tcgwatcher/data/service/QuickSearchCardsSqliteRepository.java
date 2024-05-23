package de.dktutzer.tcgwatcher.data.service;

import de.dktutzer.tcgwatcher.data.data.PokemonCardEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

public interface QuickSearchCardsSqliteRepository extends CrudRepository<PokemonCardEntity, Long> {


}
