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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TCGMapperService {

  private final ObjectMapper objectMapper;

  private final QuickSearchCardsSqliteRepository quickSearchCardsSqliteRepository;
  private final SetsSqliteRepository setsSqliteRepository;
  private final SeriesSqlRepository seriesSqlRepository;

  private final QuickSearchCardsFtsSqliteRepository quickSearchCardsFtsSqliteRepository;

  @Value("${app.dex.data.dir}")
  private String dexDataDir;



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
          normalSet.setNameEn(setModelNames.get("en"));
          normalSet.setNameDe(setModelNames.get("de"));
          normalSet.setNameFr(setModelNames.get("fr"));
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
        normalCard.setNameDe(cardNames.get("de"));
        normalCard.setNameEn(cardNames.get("en"));
        normalCard.setNameFr(cardNames.get("fr"));
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

        // prefer ptcgoCode as 'code', fallback to an abbreviation if no ptcgoCode
        String code = set.ptcgoCode();

        String abbreviation = "";
        if (set.abbreviations() != null && !set.abbreviations().isEmpty()) {
          // prefer english abbrev if present, otherwise take first available
          abbreviation = set.abbreviations().getOrDefault("en", set.abbreviations().values().stream().findFirst().orElse(""));
        }

        if (!hasText(code)) {
          code = hasText(abbreviation) ? abbreviation : null;
        }

        Integer numberTotal = set.total();
        Integer numberOfficial = set.printedTotal();
        Map<String, String> names = set.name() == null ? Map.of() : set.name();

        var model = TCGWatcherSetModel.builder()
            .id(set.id())
            .numberTotal(numberTotal)
            .numberOfficial(numberOfficial)
            .code(code)
            .abbreviation(abbreviation)
            .names(names)
            .series(seriesModel)
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
          // there is no strict guaranteed field for a separate cm code in the dex data;
          // keep cmCode null unless a specific key exists
          String cmCode = thirdParty.get("cardmarketCode");

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

  private PokemonSeriesEntity findOrCreateSeriesByName(TCGWatcherSeriesModel series) {

    var optional = seriesSqlRepository.findById(series.getId());
    if (optional.isPresent()) {
      return optional.get();
    }
    var pokemonSeriesEntity = new PokemonSeriesEntity();
    pokemonSeriesEntity.setId(series.getId());
    pokemonSeriesEntity.setNameDe(series.getNames().get("de"));
    pokemonSeriesEntity.setNameEn(series.getNames().get("en"));
    pokemonSeriesEntity.setNameFr(series.getNames().get("fr"));
    seriesSqlRepository.save(pokemonSeriesEntity);
    return  pokemonSeriesEntity;
  }
}
