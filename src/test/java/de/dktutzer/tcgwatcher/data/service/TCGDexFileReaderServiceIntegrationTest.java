package de.dktutzer.tcgwatcher.data.service;

import static org.junit.jupiter.api.Assertions.*;

import de.dktutzer.tcgwatcher.data.data.model.DexCardData;
import de.dktutzer.tcgwatcher.data.data.model.DexSetData;
import de.dktutzer.tcgwatcher.data.data.model.DexSeriesData;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class TCGDexFileReaderServiceIntegrationTest {

  @Test
  void readsSeriesSetsAndCardsCorrectly_happyPath(@TempDir Path tmp) throws Exception {
    // arrange: build directory structure
    Path base = tmp.resolve("data");
    Files.createDirectories(base);

    // series metadata file in base
    String seriesContent = "id: 'base'\nname: { en: 'Base Series' }\n";
    Files.writeString(base.resolve("Base.ts"), seriesContent, StandardCharsets.UTF_8);

    // create series folder
    Path seriesFolder = base.resolve("base");
    Files.createDirectories(seriesFolder);

    // create set folder with metadata
    Path setFolder = seriesFolder.resolve("set1");
    Files.createDirectories(setFolder);
    String setContent = "id: 'set1'\nname: { en: 'First Set' }\ncardCount: { official: 100 } \nptcgoCode: 'P1'\nimages: { symbol: 'img.png' }\n";
    Files.writeString(setFolder.resolve("set1.ts"), setContent, StandardCharsets.UTF_8);

    // create card file
    String cardContent = "id: 'card1'\nname: { en: 'Pikachu' }\nnumber: '12'\nsupertype: 'Pok\u00e9mon'\ntypes: ['Electric']\ndexId: [25]\nabilities: [{ name: 'Static', text: 'paralyze' }]\nattacks: [{ name: 'Thunder', cost: ['Electric'], damage: '30' }]\ndescription: { en: 'A friendly mouse.' }\nillustrator: 'Ash'\n";
    Files.writeString(setFolder.resolve("card1.ts"), cardContent, StandardCharsets.UTF_8);

    // act

    Map<String, DexSeriesData> result = TCGDexService.readAllSeries(base.toString());

    // assert
    assertNotNull(result);
    assertTrue(result.containsKey("base"));
    DexSeriesData series = result.get("base");
    assertEquals("Base Series", series.name().get("en"));
    assertTrue(series.sets().containsKey("set1"));

    DexSetData set = series.sets().get("set1");
    assertEquals("First Set", set.name().get("en"));
    assertEquals(Integer.valueOf(100), set.cardCount());
    assertEquals("P1", set.ptcgoCode());
    assertEquals("img.png", set.images().get("symbol"));

    assertTrue(set.cards().containsKey("card1"));
    DexCardData card = set.cards().get("card1");
    assertEquals("12", card.number());
    assertEquals(List.of(25), card.dexId());
    assertEquals("A friendly mouse.", card.description().get("en"));
    assertEquals(1, card.abilities().size());
    assertEquals("Static", card.abilities().get(0).get("name"));
  }

  @Test
  void fallsBackToFolderAndFilenameWhenMetadataMissing_edgeCases(@TempDir Path tmp) throws Exception {
    // arrange: base with a series folder that has no series .ts file
    Path base = tmp.resolve("data2");
    Files.createDirectories(base);

    Path seriesFolder = base.resolve("NoMetaSeries");
    Files.createDirectories(seriesFolder);

    Path setFolder = seriesFolder.resolve("SetA");
    Files.createDirectories(setFolder);

    // no set metadata file -> fallback to folder name
    // card file without id and name -> fallback to filename
    String cardContent = "supertype: 'Trainer'\nrarity: 'Common'\nretreat: 'X'\n"; // retreat non-numeric
    Files.writeString(setFolder.resolve("cardB.ts"), cardContent, StandardCharsets.UTF_8);

    // act

    Map<String, DexSeriesData> result = TCGDexService.readAllSeries(base.toString());

    // assert
    assertNotNull(result);
    // series key should be the folder name
    assertTrue(result.containsKey("NoMetaSeries"));
    DexSeriesData series = result.get("NoMetaSeries");
    // name fallback to folder name
    assertEquals("NoMetaSeries", series.name().get("en"));
    assertTrue(series.sets().containsKey("SetA"));

    DexSetData set = series.sets().get("SetA");
    // set name fallback to folder name
    assertEquals("SetA", set.name().get("en"));

    // card id fallback to filename without extension
    assertTrue(set.cards().containsKey("cardB"));
    DexCardData card = set.cards().get("cardB");
    // retreat was non-numeric, so retreat should be null
    assertNull(card.retreat());
    assertEquals("Trainer", card.supertype());
    assertEquals("Common", card.rarity());
  }
}
