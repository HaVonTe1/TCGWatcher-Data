package de.dktutzer.tcgwatcher.data.service.persistence;

import de.dktutzer.tcgwatcher.data.data.entities.PokemonCardEntity;
import org.springframework.data.repository.CrudRepository;

public interface QuickSearchCardsSqliteRepository extends CrudRepository<PokemonCardEntity, Long> {


}
