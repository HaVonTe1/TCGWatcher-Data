package de.dktutzer.tcgwatcher.data.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

public class TCGDexFileReaderRunnerTest {

  @Test
  void runReaderAndPrintSummary() throws Exception {
    var service = new TCGDexFileReaderService();
    String base = "src/main/resources/dexdata";
    Map<String, TCGDexFileReaderService.SeriesData> series = service.readAllSeries(base);
    assertNotNull(series);
    int totalSeries = series.size();
    int totalSets = series.values().stream().mapToInt(s -> s.sets().size()).sum();
    int totalCards = series.values().stream().flatMap(s -> s.sets().values().stream()).mapToInt(set -> set.cards().size()).sum();

    System.out.println("TCGDex data summary for: " + base);
    System.out.println("Total series: " + totalSeries);
    System.out.println("Total sets: " + totalSets);
    System.out.println("Total cards: " + totalCards);

    // print top 10 series with counts
    series.entrySet().stream()
        .sorted((a,b) -> Integer.compare(b.getValue().sets().size(), a.getValue().sets().size()))
        .limit(10)
        .forEach(e -> System.out.println(String.format("Series '%s' (%s) -> sets=%d", e.getKey(), e.getValue().name().getOrDefault("en", "?"), e.getValue().sets().size())));

    // basic assertions to fail if nothing found
    assertTrue(totalSeries > 0, "expected at least one series");
    assertTrue(totalSets > 0, "expected at least one set");
  }
}
