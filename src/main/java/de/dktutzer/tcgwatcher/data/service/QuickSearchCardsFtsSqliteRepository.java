package de.dktutzer.tcgwatcher.data.service;

import de.dktutzer.tcgwatcher.data.data.PokemonCardEntity;
import de.dktutzer.tcgwatcher.data.data.PokemonCardFtsEntity;
import org.springframework.data.repository.CrudRepository;

public interface QuickSearchCardsFtsSqliteRepository extends CrudRepository<PokemonCardFtsEntity, String > {


}
