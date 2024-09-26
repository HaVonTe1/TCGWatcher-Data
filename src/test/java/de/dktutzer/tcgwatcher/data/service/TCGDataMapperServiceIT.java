package de.dktutzer.tcgwatcher.data.service;

import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

//This "UnitTests" purpose is to be able to execute single methods without the need of starting the app.
//Sorry.. i know this is really bad

@SpringBootTest(properties = "spring.shell.interactive.enabled=false")
class TCGDataMapperServiceIT {

  @Autowired
  private TCGMapperService tcgMapperService;

  @Test
  @DisplayName("Testing reading sources and writing to json")
  void testRead() throws IOException {
    tcgMapperService.readFromSourceAndWriteToJson();
  }

  @Test
  @DisplayName("Testing reading the prepared json files and writing it to sqlite")
  void testDB() throws IOException {

    tcgMapperService.readFromJsonAndWriteToSqlite();

  }

  @Test
  @DisplayName("Testing the extraction of the cardmarket_id from the prices.pokemontcg.io api")
  void testGetCmIdViaTcgApi() throws IOException {

    tcgMapperService.getCardmarketIdFromPokemonApiById("https://prices.pokemontcg.io/cardmarket/swsh3-174");
  }
}
