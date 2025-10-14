package de.dktutzer.tcgwatcher.data.service;

import static org.springframework.util.StringUtils.hasText;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dktutzer.tcgwatcher.data.data.entities.PokemonCardEntity;
import de.dktutzer.tcgwatcher.data.data.entities.PokemonCardFtsEntity;
import de.dktutzer.tcgwatcher.data.data.entities.PokemonSeriesEntity;
import de.dktutzer.tcgwatcher.data.data.entities.PokemonSetEntity;
import de.dktutzer.tcgwatcher.data.data.dto.PokemonTcgApiCardDto;
import de.dktutzer.tcgwatcher.data.data.model.TCGDexCardDetailsModel;
import de.dktutzer.tcgwatcher.data.data.model.TCGDexCardListModel;
import de.dktutzer.tcgwatcher.data.data.dto.TCGDexSetDetailsDto;
import de.dktutzer.tcgwatcher.data.data.dto.TCGDexSetDetailsDto.Series;
import de.dktutzer.tcgwatcher.data.data.model.TCGDexSetListModel;
import de.dktutzer.tcgwatcher.data.data.model.TCGWatcherCardModel;
import de.dktutzer.tcgwatcher.data.data.model.TCGWatcherSeriesModel;
import de.dktutzer.tcgwatcher.data.data.model.TCGWatcherSetModel;
import de.dktutzer.tcgwatcher.data.service.persistence.QuickSearchCardsFtsSqliteRepository;
import de.dktutzer.tcgwatcher.data.service.persistence.QuickSearchCardsSqliteRepository;
import de.dktutzer.tcgwatcher.data.service.persistence.SeriesSqlRepository;
import de.dktutzer.tcgwatcher.data.service.persistence.SetsSqliteRepository;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.util.Pair;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class TCGMapperService {

  public static final String CARDS_JSON_FILE = "./output/cardsFromTCGDex.json";
  public static final String SETS_JSON_FILE = "./output/setsFromTcgDexWithCode.json";

  private final TCGDataReader tcgDataReader;

  private final RestTemplate restTemplate;

  private final ObjectMapper objectMapper;

  private final QuickSearchCardsSqliteRepository quickSearchCardsSqliteRepository;
  private final SetsSqliteRepository setsSqliteRepository;
  private final SeriesSqlRepository seriesSqlRepository;

  private final QuickSearchCardsFtsSqliteRepository quickSearchCardsFtsSqliteRepository;

  private final Map<SetKey, TCGDexSetDetailsDto> setnameCache = new ConcurrentHashMap<>(250);
  @Value("${app.adaptors.tcgdex.baseuri}")
  private String tcgdexuri;

  // Compile the pattern
  private static final Pattern cmLinkExtractor =
      Pattern.compile("\\\\/Pokemon\\\\/Products\\\\/Singles\\\\/([\\w\\-]+)\\\\/([\\w\\-]+)");

  private List<TCGDexCardListModel> readDexCards() {
    log.debug("Getting all Cards...");
    var listUri = tcgdexuri + "v2/de/cards/";
    ResponseEntity<List<TCGDexCardListModel>> responseEntity =
        restTemplate.exchange(listUri, HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
    var cardListDtos = responseEntity.getBody();
    assert cardListDtos != null;
    log.debug("Found: {}", cardListDtos.size());
    return cardListDtos;
  }

  private TCGDexCardDetailsModel getCardById(final String id, final String lang) {

    log.debug("Getting Card with ID: [{}] for lang: {}", id, lang);
    var cardUri = tcgdexuri + "v2/" + lang + "/cards/" + id;

    var cardDetailsDto = restTemplate.getForObject(cardUri, TCGDexCardDetailsModel.class);
    log.debug("Found: {}", cardDetailsDto);
    return cardDetailsDto;
  }

  private Map<String, TCGWatcherSetModel> readDexSets() {
    var listUri =  tcgdexuri +"v2/en/sets/";
    ResponseEntity<List<TCGDexSetListModel>> responseEntity =
        restTemplate.exchange(listUri, HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
    var dexSetListModelList = responseEntity.getBody();
    assert dexSetListModelList != null;
    return dexSetListModelList.stream()
        .map(
            setListModel -> {
              var setGerman = getSetByIdAndLang(setListModel.getId(), "de");
              var setFrench = getSetByIdAndLang(setListModel.getId(), "fr");
              var setEnglish = getSetByIdAndLang(setListModel.getId(), "en");

              // lets hope the series names are not empty in all languages...
              var namesMap = new HashMap<String, String>();
              namesMap.put(
                  "de", mergeName(setGerman.getName(), setEnglish.getName(), setFrench.getName()));
              namesMap.put(
                  "fr", mergeName(setFrench.getName(), setEnglish.getName(), setFrench.getName()));
              namesMap.put(
                  "en", mergeName(setEnglish.getName(), setFrench.getName(), setFrench.getName()));

              var seriesMap = new HashMap<String, String>();
              seriesMap.put(
                  "de",
                  mergeName(
                      setGerman.getSerie().getName(),
                      setEnglish.getSerie().getName(),
                      setFrench.getSerie().getName()));
              seriesMap.put(
                  "en",
                  mergeName(
                      setEnglish.getSerie().getName(),
                      setFrench.getSerie().getName(),
                      setGerman.getSerie().getName()));
              seriesMap.put(
                  "fr",
                  mergeName(
                      setFrench.getSerie().getName(),
                      setEnglish.getSerie().getName(),
                      setGerman.getSerie().getName()));

              var tcgWatcherSeriesModel = TCGWatcherSeriesModel.builder()
                  .id(setEnglish.getSerie().getId())
                  .names(seriesMap)
                  .build();
              return TCGWatcherSetModel.builder()
                  .id(setListModel.getId())
                  .names(namesMap)
                  .abbreviation(setEnglish.getAbbreviation()!= null ? setEnglish.getAbbreviation().getOfficial() : "")
                  .numberOfficial(setEnglish.getCardCount().getOfficial())
                  .numberTotal(setEnglish.getCardCount().getTotal())
                  .series(tcgWatcherSeriesModel)
                  .build();
            })
        .collect(Collectors.toMap(TCGWatcherSetModel::getId, set -> set));
  }

  private TCGDexSetDetailsDto getSetByIdAndLang(String id, String lang) {
    var setKey = new SetKey(id, lang);
    if (setnameCache.containsKey(setKey)) {
      return setnameCache.get(setKey);
    }

    var uri = tcgdexuri + "v2/" + lang + "/sets/" + id;
    log.debug("TCGDex: Getting Set with id: [{}] for lang: {}", id, lang);
    var dexSetDetailsDto = restTemplate.getForObject(uri, TCGDexSetDetailsDto.class);
    assert dexSetDetailsDto != null;
    if(dexSetDetailsDto.getSerie() == null) {
      dexSetDetailsDto.setSerie(new Series());//prevent npe later
    }
    log.debug("TCGDex: Found: {}", dexSetDetailsDto);
    setnameCache.put(setKey, dexSetDetailsDto);
    return dexSetDetailsDto;
  }

  public List<TCGWatcherCardModel> readFromSourceAndWriteToJson() throws IOException {
    log.info("lets go");

    var setsFromTCGDexRaw = readDexSets();
    var setsFromTCGDataRaw = tcgDataReader.readSetsFromFile();

    var setsForTCGWatcherMerged = mergeSets(setsFromTCGDexRaw, setsFromTCGDataRaw);
    var setsForTCGWatcherAsList = setsForTCGWatcherMerged.values();
    var setsJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(setsForTCGWatcherAsList);
    log.info("Writing sets to file");
    try (FileWriter writer = new FileWriter(SETS_JSON_FILE)) {
      writer.write(setsJson);
    } catch (IOException e) {
      throw new IllegalArgumentException(e);
    }

    log.info("reading cards..");
    var cardsFromTCGDex = readDexCards();

    var counter = new AtomicInteger(0);
    var mappedCards =
        cardsFromTCGDex.parallelStream()
            .map(
                card -> {
                  log.info(
                      "Handling: [{}] - [{}] with id: [{}]",
                      counter.addAndGet(1),
                      card.getName(),
                      card.getId());


                  {
                    var cardDexGerman = getCardById(card.getId(), "de");
                    var cardDexEng = getCardById(card.getId(), "en");
                    var cardDexFrench = getCardById(card.getId(), "fr");

                    if (hasText(cardDexGerman.getName())
                        || hasText(cardDexEng.getName())
                        || hasText(cardDexFrench.getName())) {
                      var nameGer =
                          mergeName(
                              cardDexGerman.getName(), cardDexEng.getName(), cardDexFrench.getName());
                      var nameEng =
                          mergeName(
                              cardDexEng.getName(), cardDexGerman.getName(), cardDexFrench.getName());
                      var nameFr =
                          mergeName(
                              cardDexFrench.getName(), cardDexEng.getName(), cardDexGerman.getName());
                      var names = Map.of("en", nameEng, "de", nameGer, "fr", nameFr);

                      var tcgWatcherPokemon =
                          TCGWatcherCardModel.builder()
                              .id(card.getId())
                              .names(names)
                              .number(cardDexEng.getLocalId())
                              .setId(cardDexEng.getSet().getId())
                              .build();

                      log.info("Adding: {}", tcgWatcherPokemon);
                      return tcgWatcherPokemon;
                    }
                  }

                  return null;
                })
            .toList();

    var cardsJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(mappedCards);
    log.debug("Writing cards to file");
    try (FileWriter writer = new FileWriter(CARDS_JSON_FILE)) {
      writer.write(cardsJson);
    } catch (IOException e) {
      throw new IllegalArgumentException(e);
    }
    log.info("done");
    return mappedCards;
  }





  private Map<String, TCGWatcherSetModel> mergeSets(
      Map<String, TCGWatcherSetModel> setsFromTCGDexRaw,
      Map<String, TCGWatcherSetModel> setsFromTCGDataRaw) {
    // the dex data contains all info EXCEPT the 'code'
    // the tcgdata file contains the code, but only the english name...

    // so we search the english name from both setlists and merge the i18n names into the tcgdata
    // list

    setsFromTCGDexRaw.forEach((dexSetId, watcherSetModel) -> {
      var code = findMatchingSetCode(watcherSetModel, setsFromTCGDataRaw);
      if (hasText(code)) {
        watcherSetModel.setCode(code);
      }
        });
    return setsFromTCGDexRaw;
  }

  private String findMatchingSetCode(TCGWatcherSetModel watcherSetModel, Map<String, TCGWatcherSetModel> setsFromTCGDataRaw) {
    var watcherNameEn = watcherSetModel.getNames().get("en");
    return setsFromTCGDataRaw.values().parallelStream()
        .filter(dataSet -> watcherNameEn.compareTo(dataSet.getNames().get("en")) == 0)
        .map(TCGWatcherSetModel::getCode)
        .filter(StringUtils::hasText)
        .findFirst()
        .orElse(null);
  }

  private String mergeName(String name1, String name2, String name3) {
    if (hasText(name1)) {
      return name1;
    }
    if (hasText(name2)) {
      return name2;
    }
    if (hasText(name3)) {
      return name3;
    }
    return "";
  }

  @Data
  @AllArgsConstructor
  private static class SetKey {
    private String id;
    private String lang;
  }

  /*
  reads the jsons and writes the data in an easy to query datebase

  it would be possible to this without the json step but i am lazy today
   */
  public void readFromJsonAndWriteToSqlite() throws IOException {

    var sets = objectMapper.readValue(new File(SETS_JSON_FILE), new TypeReference<ArrayList<TCGWatcherSetModel>>() {});
    var cards = objectMapper.readValue(new File(CARDS_JSON_FILE), new TypeReference<ArrayList<TCGWatcherCardModel>>() {});

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
