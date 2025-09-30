package de.dktutzer.tcgwatcher.data.service.persistence;

import de.dktutzer.tcgwatcher.data.data.entities.PokemonCardFtsEntity;
import org.springframework.data.repository.CrudRepository;

public interface QuickSearchCardsFtsSqliteRepository extends CrudRepository<PokemonCardFtsEntity, String > {


}
