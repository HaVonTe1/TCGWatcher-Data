package de.dktutzer.tcgwatcher.data.service.persistence;

import de.dktutzer.tcgwatcher.data.data.entities.PokemonSetEntity;
import org.springframework.data.repository.CrudRepository;

public interface SetsSqliteRepository  extends CrudRepository<PokemonSetEntity, String> {}
