package de.dktutzer.tcgwatcher.data.service;

import static org.springframework.util.StringUtils.hasText;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dktutzer.tcgwatcher.data.data.PokemonCardEntity;
import de.dktutzer.tcgwatcher.data.data.PokemonCardFtsEntity;
import de.dktutzer.tcgwatcher.data.data.PokemonTcgApiCardDto;
import de.dktutzer.tcgwatcher.data.data.TCGDexCardDetailsModel;
import de.dktutzer.tcgwatcher.data.data.TCGDexCardListModel;
import de.dktutzer.tcgwatcher.data.data.TCGDexSetDetailsModel;
import de.dktutzer.tcgwatcher.data.data.TCGDexSetDetailsModel.SeriesModel;
import de.dktutzer.tcgwatcher.data.data.TCGDexSetListModel;
import de.dktutzer.tcgwatcher.data.data.TCGWatcherCardModel;
import de.dktutzer.tcgwatcher.data.data.TCGWatcherSetModel;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

  private final QuickSearchCardsFtsSqliteRepository quickSearchCardsFtsSqliteRepository;

  private final Map<SetKey, TCGDexSetDetailsModel> setnameCache = new ConcurrentHashMap<>(250);
  @Value("${app.adaptors.tcgdex.baseuri}")
  private String tcgdexuri;
  @Value("${app.adaptors.pokemontcgapi.baseuri}")
  private String pkmTcgApiBasePath;

  @Value("${app.adaptors.pokemontcgapi.apikey}")
  private String pokemonApiTcgApiKey;

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

              return TCGWatcherSetModel.builder()
                  .id(setListModel.getId())
                  .names(namesMap)
                  .number(setEnglish.getCardCount().getOfficial())
                  .series(seriesMap)
                  .build();
            })
        .collect(Collectors.toMap(TCGWatcherSetModel::getId, set -> set));
  }

  private TCGDexSetDetailsModel getSetByIdAndLang(String id, String lang) {
    var setKey = new SetKey(id, lang);
    if (setnameCache.containsKey(setKey)) {
      return setnameCache.get(setKey);
    }

    var uri = tcgdexuri + "v2/" + lang + "/sets/" + id;
    log.debug("TCGDex: Getting Set with id: [{}] for lang: {}", id, lang);
    var dexSetDetailsDto = restTemplate.getForObject(uri, TCGDexSetDetailsModel.class);
    assert dexSetDetailsDto != null;
    if(dexSetDetailsDto.getSerie() == null) {
      dexSetDetailsDto.setSerie(new SeriesModel());//prevent npe later
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
    var setsJson = objectMapper.writeValueAsString(setsForTCGWatcherAsList);
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

                  var cmPair = getCardmarketIdFromPokemonTcgApiById(card.getId());

                  //We dont want cards without a cmID
                  if (cmPair != null)
                  {
                    log.debug("Found cmId: [{}]", cmPair);
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
                              .cmSetId(cmPair.getFirst())
                              .cmCardId(cmPair.getSecond())
                              .build();

                      log.info("Adding: {}", tcgWatcherPokemon);
                      return tcgWatcherPokemon;
                    }
                  }

                  return null;
                })
            .toList();

    var cardsJson = objectMapper.writeValueAsString(mappedCards);
    log.debug("Writing cards to file");
    try (FileWriter writer = new FileWriter(CARDS_JSON_FILE)) {
      writer.write(cardsJson);
    } catch (IOException e) {
      throw new IllegalArgumentException(e);
    }
    log.info("done");
    return mappedCards;
  }

  private Pair<String, String> getCardmarketIdFromPokemonTcgApiById(String id) {
    log.debug("PokemonTcgApi: Getting Card with ID: {}", id);

    var cardUri = this.pkmTcgApiBasePath + id;

    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Api-Key", this.pokemonApiTcgApiKey);

    // Create the request entity with headers
    HttpEntity<String> entity = new HttpEntity<>(headers);

    // Execute the request
    ResponseEntity<PokemonTcgApiCardDto> response = restTemplate.exchange(
        cardUri,
        HttpMethod.GET,
        entity,
        PokemonTcgApiCardDto.class
    );
    if(response.getStatusCode().is2xxSuccessful()) {

      // Return the body of the response
      var cardDetailsDto = response.getBody();

      log.debug("PokemonTcgApi: Found: {}", cardDetailsDto);

      if(cardDetailsDto!=null && cardDetailsDto.getData()!=null && cardDetailsDto.getData().getCardmarket()!=null)
      {
        var cmUrl = cardDetailsDto.getData().getCardmarket().getUrl();
        return getCardmarketIdFromPokemonApiById(cmUrl);
      }

    }
    log.debug("PokemonTcgApi: not found");

    return null;

  }


  /*
  i have no idea why but requesting the prices.pokemontcg.io api results in a 403.
  Doing the same in the browser or with curl works just fine. Must be some cloudflare stuff...
  BUT the error message contains the cardmarket uri we want. So...
   */
  public Pair<String, String> getCardmarketIdFromPokemonApiById(String pricesPokemonApiUri) {
    var cmHeader = new HttpHeaders();

    var cmHttpEntity = new HttpEntity<>(pricesPokemonApiUri, cmHeader);

    ResponseEntity<String> cmResponse = restTemplate.exchange(
        pricesPokemonApiUri,
        HttpMethod.GET,
        cmHttpEntity,
        String.class
    );

    var body = cmResponse.getBody();
    if(StringUtils.hasText(body)) {
      // Create a matcher from the input string
      Matcher matcher = cmLinkExtractor.matcher(body);

      if (matcher.find() && matcher.groupCount()==2) {
        var cmSetId = matcher.group(1);
        var cmCardId = matcher.group(2);
        if(StringUtils.hasText(cmSetId) && StringUtils.hasText(cmCardId) ) {
          return  Pair.of(cmSetId, cmCardId);
        }
        return null;
      }

    }
    return  null;
  }

  private Map<String, TCGWatcherSetModel> mergeSets(
      Map<String, TCGWatcherSetModel> setsFromTCGDexRaw,
      Map<String, TCGWatcherSetModel> setsFromTCGDataRaw) {
    // the dex data contains all info EXCEPT the 'code'
    // the tcgdata file contains the code, but only the english name...

    // so we search the english name from both setlists and merge the i18n names into the tcgdata
    // list

    setsFromTCGDexRaw.forEach(
        (dexSetId, watcherSetModel) -> {
          var matchingTcgDataSet =
              setsFromTCGDataRaw.values().stream()
                  .filter(
                      dataSet -> {
                        var tcgDataNamesMap = dataSet.getNames();
                        var watcherNamesMap = watcherSetModel.getNames();

                        return watcherNamesMap.get("en").compareTo(tcgDataNamesMap.get("en"))
                            == 0;
                      })
                  .findFirst();
          matchingTcgDataSet.ifPresent(
              tcgWatcherSetModel -> watcherSetModel.setCode(tcgWatcherSetModel.getCode()));
        });
    return setsFromTCGDexRaw;
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

    var normalizedCards = new ArrayList<PokemonCardEntity>();
    var ftsCards = new ArrayList<PokemonCardFtsEntity>();

    cards.forEach(card -> {
      if (card != null) {
        var normalCard = new PokemonCardEntity();
        var ftsCard = new PokemonCardFtsEntity();

        var setModelOptional = sets.stream().filter(set -> set.getId().equalsIgnoreCase(card.getSetId())).findFirst();
        String setCode = card.getSetId().toUpperCase(Locale.ROOT);
        if (setModelOptional.isPresent()) {
          var code = setModelOptional.get().getCode();
          if (hasText(code)) {
            setCode = code.toUpperCase();
          }
        }
        var fullCardCode = String.format("%s %s", setCode, card.getNumber());

        normalCard.setCode(fullCardCode);
        normalCard.setId(card.getId());
        normalCard.setNameDe(card.getNames().get("de"));
        normalCard.setNameEn(card.getNames().get("en"));
        normalCard.setNameFr(card.getNames().get("fr"));
        normalCard.setCmCardId(card.getCmCardId());
        normalCard.setCmSetId(card.getCmSetId());


        ftsCard.setId(card.getId());
        ftsCard.setCode(card.getNumber());
        ftsCard.setNames(String.format("%s %s %s", card.getNames().get("de"), card.getNames().get("en"), card.getNames().get("fr")));
        normalizedCards.add(normalCard);
        ftsCards.add(ftsCard);

      }
    });

    quickSearchCardsSqliteRepository.saveAll(normalizedCards);
    quickSearchCardsFtsSqliteRepository.saveAll(ftsCards);


  }
}
