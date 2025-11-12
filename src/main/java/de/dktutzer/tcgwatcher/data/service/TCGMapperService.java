package de.dktutzer.tcgwatcher.data.service;

import static org.springframework.util.StringUtils.hasText;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dktutzer.tcgwatcher.data.data.entities.PokemonCardEntity;
import de.dktutzer.tcgwatcher.data.data.entities.PokemonCardFtsEntity;
import de.dktutzer.tcgwatcher.data.data.entities.PokemonSeriesEntity;
import de.dktutzer.tcgwatcher.data.data.entities.PokemonSetEntity;

import de.dktutzer.tcgwatcher.data.data.model.DexSeriesData;
import de.dktutzer.tcgwatcher.data.data.model.DexSetData;
import de.dktutzer.tcgwatcher.data.data.model.DexCardData;
import de.dktutzer.tcgwatcher.data.data.model.TCGWatcherCardModel;
import de.dktutzer.tcgwatcher.data.data.model.TCGWatcherSeriesModel;
import de.dktutzer.tcgwatcher.data.data.model.TCGWatcherSetModel;
import de.dktutzer.tcgwatcher.data.service.persistence.QuickSearchCardsFtsSqliteRepository;
import de.dktutzer.tcgwatcher.data.service.persistence.QuickSearchCardsSqliteRepository;
import de.dktutzer.tcgwatcher.data.service.persistence.SeriesSqlRepository;
import de.dktutzer.tcgwatcher.data.service.persistence.SetsSqliteRepository;
import jakarta.persistence.criteria.CriteriaBuilder.In;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TCGMapperService {

  private final QuickSearchCardsSqliteRepository quickSearchCardsSqliteRepository;
  private final SetsSqliteRepository setsSqliteRepository;
  private final SeriesSqlRepository seriesSqlRepository;

  private final QuickSearchCardsFtsSqliteRepository quickSearchCardsFtsSqliteRepository;

  @Value("${app.dex.data.dir}")
  private String dexDataDir;

  // cache loaded lazily from resources
  private volatile Map<String, String> cmProductIdToCodeCache = null;



  public void readFromFilesAndWriteToSqlite() throws IOException {

    var dexSeriesDataMap = TCGDexService.readAllSeries(dexDataDir);

    List<TCGWatcherCardModel> cards = convertDexCardsToTCGWatcherCards(dexSeriesDataMap);
    List<TCGWatcherSetModel> sets = convertDexSetsToTCGWatcherSets(dexSeriesDataMap);


    quickSearchCardsSqliteRepository.deleteAll();
    quickSearchCardsFtsSqliteRepository.deleteAll();
    setsSqliteRepository.deleteAll();
    seriesSqlRepository.deleteAll();

    var normalizedCards = new ArrayList<PokemonCardEntity>();
    var ftsCards = new ArrayList<PokemonCardFtsEntity>();
    var nomalizedSets = new ArrayList<PokemonSetEntity>();

    cards.forEach(card -> {
      if (card != null) {
        var normalCard = new PokemonCardEntity();
        var normalSet = new PokemonSetEntity();
        var ftsCard = new PokemonCardFtsEntity();

        var setModelOptional = sets.stream().filter(set -> set.getId().equalsIgnoreCase(card.getSetId())).findFirst();
        String setCode = card.getSetId().toUpperCase(Locale.ROOT);
        if (setModelOptional.isPresent()) {
          var tcgWatcherSetModel = setModelOptional.get();
          var code = tcgWatcherSetModel.getCode();
          if (hasText(code)) {
            setCode = code.toUpperCase();
          }

          var series = findOrCreateSeriesByName(tcgWatcherSetModel.getSeries());

          normalSet.setCode(setCode);
          normalSet.setId(tcgWatcherSetModel.getId());
          var setModelNames = tcgWatcherSetModel.getNames();
          normalSet.setNameEn(setModelNames.getOrDefault("en", ""));
          normalSet.setNameDe(setModelNames.getOrDefault("de", ""));
          normalSet.setNameFr(setModelNames.getOrDefault("fr", ""));
          normalSet.setSeries(series);
          normalSet.setOfficial(tcgWatcherSetModel.getNumberOfficial());
          normalSet.setTotal(tcgWatcherSetModel.getNumberTotal());
          normalSet.setAbbreviation(tcgWatcherSetModel.getAbbreviation());

          nomalizedSets.add(normalSet);

        }
        var fullCardCode = String.format("%s %s", setCode, card.getNumber());

        normalCard.setCode(fullCardCode);
        normalCard.setId(card.getId());
        var cardNames = card.getNames();
        normalCard.setNameDe(cardNames.getOrDefault("de", ""));
        normalCard.setNameEn(cardNames.getOrDefault("en", ""));
        normalCard.setNameFr(cardNames.getOrDefault("fr", ""));
        normalCard.setSetId(card.getSetId());


        ftsCard.setId(card.getId());
        ftsCard.setCode(card.getNumber());
        ftsCard.setNames(String.format("%s %s %s", cardNames.get("de"), cardNames.get("en"), cardNames.get("fr")));
        normalizedCards.add(normalCard);
        ftsCards.add(ftsCard);

      }
    });

    setsSqliteRepository.saveAll(nomalizedSets);
    quickSearchCardsSqliteRepository.saveAll(normalizedCards);
    quickSearchCardsFtsSqliteRepository.saveAll(ftsCards);


  }

  private List<TCGWatcherSetModel> convertDexSetsToTCGWatcherSets(Map<String, DexSeriesData> dexSeriesDataMap) {

    // iterate all series and their sets and convert to TCGWatcherSetModel
    List<TCGWatcherSetModel> result = new ArrayList<>();
    if (dexSeriesDataMap == null) return result;

    for (DexSeriesData seriesData : dexSeriesDataMap.values()) {
      if (seriesData == null) continue;
      var seriesModel = new TCGWatcherSeriesModel(seriesData.id(), seriesData.name());
      if (seriesData.sets() == null) continue;
      for (DexSetData set : seriesData.sets().values()) {
        if (set == null) continue;

        String code = set.id().toUpperCase(Locale.ROOT); //fallback if no matching abbr

        String abbreviation = "";
        if (set.abbreviations() != null && !set.abbreviations().isEmpty()) {
          // prefer english abbrev if present, otherwise take first available
          abbreviation = set.abbreviations().getOrDefault("en", set.abbreviations().values().stream().findFirst().orElse(""));
        }

        if (!hasText(code)) {
          code = hasText(abbreviation) ? abbreviation : null;
        }

        Integer numberOfficial = set.cardCount() !=null ? set.cardCount(): 0;
        Integer numberTotal = 0; //needs to be read from tcgdata
        Map<String, String> names = set.name() == null ? Map.of() : set.name();
        var cmId = set.thirdParty().getOrDefault("cardmarket","");
        var tcgpId = set.thirdParty().getOrDefault("tcgplayer","");

        var model = TCGWatcherSetModel.builder()
            .id(set.id())
            .numberTotal(0)
            .numberOfficial(numberOfficial)
            .code(code)
            .abbreviation(abbreviation)
            .names(names)
            .series(seriesModel)
            .cmSetId(cmId)
            .tcgpSetId(tcgpId)
            .build();

        result.add(model);
      }
    }

    return result;
  }

  private List<TCGWatcherCardModel> convertDexCardsToTCGWatcherCards(Map<String, DexSeriesData> dexSeriesDataMap) {

    List<TCGWatcherCardModel> result = new ArrayList<>();
    if (dexSeriesDataMap == null) return result;

    for (DexSeriesData seriesData : dexSeriesDataMap.values()) {
      if (seriesData == null || seriesData.sets() == null) continue;
      for (DexSetData set : seriesData.sets().values()) {
        if (set == null || set.cards() == null) continue;
        for (DexCardData card : set.cards().values()) {
          if (card == null) continue;

          Map<String, String> names = card.names() == null ? Map.of() : card.names();
          Map<String, String> thirdParty = card.thirdParty() == null ? Map.of() : card.thirdParty();

          String cmProductId = thirdParty.get("cardmarket");
          String tcgpId = thirdParty.get("tcgplayer");
          String cmCode = ""; //needs to read from the csv

          var model = TCGWatcherCardModel.builder()
              .id(card.id())
              .names(names)
              .setId(set.id())
              .number(card.number())
              .cmProductId(cmProductId)
              .tcgpId(tcgpId)
              .cmCode(cmCode)
              .build();

          result.add(model);
        }
      }
    }

    return result;
  }

  private String readCardmarketCodeFromCSVByProductId(String cmProductId) {

    if (!hasText(cmProductId)) return null;

    ensureCmCsvLoaded();
    if (cmProductIdToCodeCache == null) return null;
    return cmProductIdToCodeCache.get(cmProductId);
  }

  private synchronized void ensureCmCsvLoaded() {
    if (cmProductIdToCodeCache != null) return;
    Map<String,String> map = new ConcurrentHashMap<>();

    InputStream is = getClass().getClassLoader().getResourceAsStream("product-id-to-url-cardmarket.csv.zip");
    if (is == null) {
      // resource not present
      cmProductIdToCodeCache = map;
      return;
    }

    try (ZipInputStream zis = new ZipInputStream(is, StandardCharsets.UTF_8)) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (entry.isDirectory()) continue;
        String name = entry.getName();
        if (name == null) continue;
        if (!name.toLowerCase().endsWith(".csv")) continue;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8))) {
          String line;
          while ((line = br.readLine()) != null) {
            if (line.isBlank()) continue;
            // split into two columns only (id, url) - url may be quoted
            String[] parts = line.split(",", 2);
            if (parts.length < 2) continue;
            String id = stripQuotes(parts[0].trim());
            // skip header
            if (id.equalsIgnoreCase("cardmarket_product_id")) continue;
            String url = stripQuotes(parts[1].trim());
            if (id.isEmpty() || url.isEmpty()) continue;
            // extract last segment after '/'
            int slash = url.lastIndexOf('/');
            String code = (slash >= 0 && slash < url.length()-1) ? url.substring(slash+1) : url;
            // remove query string if any
            int q = code.indexOf('?');
            if (q >= 0) code = code.substring(0, q);
            if (!code.isBlank()) {
              map.put(id, code);
            }
          }
        }
      }
    } catch (Exception e) {
      log.warn("Failed to load product-id-to-url-cardmarket.csv.zip: {}", e.getMessage());
    }

    cmProductIdToCodeCache = map;
  }

  private static String stripQuotes(String s) {
    if (s == null) return "";
    s = s.trim();
    if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
      return s.substring(1, s.length()-1);
    }
    return s;
  }

  private PokemonSeriesEntity findOrCreateSeriesByName(TCGWatcherSeriesModel series) {

    var optional = seriesSqlRepository.findById(series.getId());
    if (optional.isPresent()) {
      return optional.get();
    }
    var pokemonSeriesEntity = new PokemonSeriesEntity();
    pokemonSeriesEntity.setId(series.getId());
    pokemonSeriesEntity.setNameDe(series.getNames().getOrDefault("de" ,""));
    pokemonSeriesEntity.setNameEn(series.getNames().getOrDefault("en", ""));
    pokemonSeriesEntity.setNameFr(series.getNames().getOrDefault("fr", ""));
    seriesSqlRepository.save(pokemonSeriesEntity);
    return  pokemonSeriesEntity;
  }
}
