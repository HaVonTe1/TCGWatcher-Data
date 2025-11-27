package de.dktutzer.tcgwatcher.data.service;

import de.dktutzer.tcgwatcher.data.data.model.DexCardData;
import de.dktutzer.tcgwatcher.data.data.model.DexSeriesData;
import de.dktutzer.tcgwatcher.data.data.model.DexSetData;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.util.StringUtils;

/**
 * Utility to read a pokeapi TypeScript data dump and produce a nested map
 * structure: Series -> Sets
 * -> Cards
 */
public class TCGDexService {

  private static final String KEY_NAME = "name";
  private static final String KEY_ID = "id";
  private static final String KEY_DEX_ID = "dexId";
  private static final String KEY_EVOLVE_FROM = "evolveFrom";
  private static final String KEY_EVOLVES_FROM = "evolvesFrom";
  private static final String KEY_DESCRIPTION = "description";
  private static final String KEY_ABILITIES = "abilities";
  private static final String KEY_ATTACKS = "attacks";
  private static final String KEY_WEAKNESSES = "weaknesses";
  private static final String KEY_RESISTANCES = "resistances";
  private static final String KEY_RETREAT = "retreat";
  private static final String KEY_SUPERTYPE = "supertype";
  private static final String KEY_SUBTYPES = "subtypes";
  private static final String KEY_RARITY = "rarity";
  private static final String KEY_HP = "hp";
  private static final String KEY_TYPES = "types";
  private static final String KEY_STAGE = "stage";
  private static final String KEY_RETREAT_COST = "retreatCost";
  private static final String KEY_CONVERTED_RETREAT_COST = "convertedRetreatCost";
  private static final String KEY_ILLUSTRATOR = "illustrator";
  private static final String KEY_THIRD_PARTY = "thirdParty";
  private static final String KEY_RELEASE_DATE = "releaseDate";
  private static final String KEY_TCG_ONLINE = "tcgOnline";
  private static final String KEY_ABBREVIATIONS = "abbreviations";
  private static final String KEY_CARD_COUNT = "cardCount";
  private static final String KEY_OFFICIAL = "official";

  private static final Pattern NAME_SIMPLE = Pattern.compile("name\\s*[:=]\\s*['\"]([^'\"]+)['\"]");
  private static final Pattern NAME_EN_IN_OBJ = Pattern
      .compile("name\\s*[:=]\\s*\\{[^}]*en\\s*[:=]\\s*['\"]([^'\"]+)['\"]", Pattern.DOTALL);
  private static final Pattern ID_SIMPLE = Pattern.compile("id\\s*[:=]\\s*['\"]([^'\"]+)['\"]");
  private static final Pattern PROPERTY_SIMPLE = Pattern.compile(
      "(\\w+)\\s*[:=]\\s*(\\{[^}]*\\}|\\[[^\\]]*\\]|'[^']*'|\"[^\"]*\"|[^,\\n]+)",
      Pattern.DOTALL);

  private TCGDexService() {
  }

  /**
   * Read the given base directory and build a map of series -> sets -> cards.
   *
   * @param baseDirPath path to the base data directory (e.g.
   *                    /.../pokeapi/cards-database/data)
   * @return map keyed by series id or folder name
   * @throws IOException on IO errors
   */
  public static Map<String, DexSeriesData> readAllSeries(String baseDirPath) throws IOException {
    Path base = Path.of(baseDirPath);
    if (!Files.isDirectory(base)) {
      return Collections.emptyMap();
    }

    Map<String, DexSeriesData> seriesMap = new HashMap<>();

    // First, read all .ts files in the base dir - these usually contain series
    // metadata
    try (Stream<Path> stream = Files.list(base)) {
      List<Path> entries = stream.toList();
      // process files to gather series metadata
      for (Path p : entries) {
        if (Files.isRegularFile(p) && p.toString().endsWith(".ts")) {
          processSeries(base, p, seriesMap);
        }
      }
    }

    return seriesMap;
  }

  private static void processSeries(
      Path base, Path seriesMetaFile, Map<String, DexSeriesData> seriesMap) {
    String content = readFileSafe(seriesMetaFile);
    String seriesId = extractIdFromContent(content)
        .orElseThrow(
            () -> new IllegalStateException(
                "Could not extract ID from series file: " + seriesMetaFile));
    String seriesFolderName = stripExt(seriesMetaFile.getFileName().toString());
    Map<String, String> seriesNameMap = extractLocalizedMap(content, KEY_NAME);

    // initialize series with empty sets; sets will be filled from subfolders
    seriesMap.putIfAbsent(
        seriesId,
        new DexSeriesData(
            seriesId,
            seriesNameMap.isEmpty() ? Map.of("en", seriesFolderName) : seriesNameMap,
            new HashMap<>()));

    DexSeriesData series = seriesMap.get(seriesId);

    // scan sets inside series folder
    Path seriesPath = base.resolve(seriesFolderName);
    if (Files.isDirectory(seriesPath)) {
      try (Stream<Path> setStream = Files.list(seriesPath)) {
        setStream.forEach(setPath -> processSet(setPath, seriesPath, series));
      } catch (IOException e) {
        throw new UncheckedIOException("Failed to list sets in " + seriesPath, e);
      }
    }
  }

  private static void processSet(Path setPath, Path seriesPath, DexSeriesData series) {
    if (!Files.isDirectory(setPath)) {
      return;
    }

    String setId = "";
    String setName = setPath.getFileName().toString();
    String setRaw = "";
    Map<String, String> setProps = Map.of();

    // attempt to find metadata file for set (index.ts or <setId>.ts)
    Optional<Path> maybeMeta = findFile(seriesPath, setName + ".ts");
    if (maybeMeta.isPresent()) {
      String setContent = readFileSafe(maybeMeta.get());
      setName = Optional.ofNullable(extractNameEn(setContent)).orElse(setName);
      setProps = parseProperties(setContent);
      setId = getString(setProps, KEY_ID);
      if (setId.isEmpty()) {
        setId = setName;
      }
      setRaw = setContent;
    }

    Map<String, DexCardData> cards = new HashMap<>();
    // read .ts files inside set folder (cards)
    try (Stream<Path> cardStream = Files.list(setPath)) {
      cardStream
          .filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".ts"))
          .forEach(cardFile -> processCard(cardFile, cards));
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to list cards in " + setPath, e);
    }

    // attempt to fill set images map from setProps
    Map<String, String> images = extractImages(setProps);
    String seriesIdRef = series.id();
    String releaseDate = getString(setProps, KEY_RELEASE_DATE);
    String releaseDateIso8601 = StringUtils.hasText(releaseDate)
        ? LocalDate.parse(releaseDate)
            .atStartOfDay()
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))
        : "";

    Map<String, String> nameMap = extractLocalizedMap(setRaw, KEY_NAME);
    Map<String, String> abbreviations = parseSubObject(setRaw, KEY_ABBREVIATIONS);
    Map<String, String> thirdPartySet = parseSubObject(setRaw, KEY_THIRD_PARTY);
    Map<String, String> cardCount = parseSubObject(setRaw, KEY_CARD_COUNT);
    String ptcgoCode = getString(setProps, KEY_TCG_ONLINE);
    Integer officialCardCount = getInteger(cardCount, KEY_OFFICIAL);

    series
        .sets()
        .put(
            setId,
            new DexSetData(
                setId,
                nameMap.isEmpty() ? Map.of("en", setName) : nameMap,
                seriesIdRef,
                officialCardCount,
                releaseDateIso8601,
                ptcgoCode,
                abbreviations,
                thirdPartySet,
                images,
                cards));
  }

  private static void processCard(Path cardFile, Map<String, DexCardData> cards) {
    String cardContent = readFileSafe(cardFile);
    String cardId = stripExt(cardFile.getFileName().toString());
    Map<String, String> cardProps = parseProperties(cardContent);

    Map<String, String> cardNames = extractLocalizedMap(cardContent, KEY_NAME);
    List<Integer> dexId = getIntegerListFromContent(cardContent, KEY_DEX_ID);
    Map<String, String> evolveFrom = extractLocalizedMap(cardContent, KEY_EVOLVE_FROM);
    if (evolveFrom.isEmpty()) {
      evolveFrom = extractLocalizedMap(cardContent, KEY_EVOLVES_FROM);
    }
    Map<String, String> description = extractLocalizedMap(cardContent, KEY_DESCRIPTION);
    List<Map<String, String>> abilities = getNestedObjectsAsMaps(cardContent, KEY_ABILITIES);
    List<Map<String, String>> attacks = getNestedObjectsAsMaps(cardContent, KEY_ATTACKS);
    List<Map<String, String>> weaknessesObj = getNestedObjectsAsMaps(cardContent, KEY_WEAKNESSES);
    List<Map<String, String>> resistancesObj = getNestedObjectsAsMaps(cardContent, KEY_RESISTANCES);
    Integer retreat = getInteger(cardProps, KEY_RETREAT);

    // build typed fields from props
    String supertype = getString(cardProps, KEY_SUPERTYPE);
    List<String> subtypes = getList(cardProps, KEY_SUBTYPES);
    String rarity = getString(cardProps, KEY_RARITY);
    String hp = getString(cardProps, KEY_HP);
    List<String> types = getList(cardProps, KEY_TYPES);
    String stage = getString(cardProps, KEY_STAGE);
    List<String> retreatCost = getList(cardProps, KEY_RETREAT_COST);
    Integer convertedRetreatCost = getInteger(cardProps, KEY_CONVERTED_RETREAT_COST);
    String artist = getString(cardProps, KEY_ILLUSTRATOR);
    Map<String, String> thirdPartyProps = parseSubObject(cardContent, KEY_THIRD_PARTY);

    cards.put(
        cardId,
        new DexCardData(
            cardId,
            cardNames,
            cardId,
            supertype,
            subtypes,
            rarity,
            hp,
            types,
            evolveFrom,
            stage,
            abilities,
            attacks,
            weaknessesObj,
            resistancesObj,
            retreatCost,
            retreat,
            convertedRetreatCost,
            artist,
            description,
            thirdPartyProps,
            dexId,
            cardFile));
  }

  // --- helpers to extract structured pieces

  private static Map<String, String> extractLocalizedMap(String content, String key) {
    Map<String, String> result = new HashMap<>();
    if (content == null || content.isBlank())
      return result;
    Matcher m = Pattern.compile(key + "\\s*[:=]\\s*\\{([^}]*)\\}", Pattern.DOTALL).matcher(content);
    if (m.find()) {
      String inner = m.group(1);
      Matcher pm = Pattern.compile("(\\w+)\\s*[:=]\\s*['\"]([^'\"]+)['\"]").matcher(inner);
      while (pm.find()) {
        result.put(pm.group(1), pm.group(2));
      }
    }
    return result;
  }

  private static List<Integer> getIntegerListFromContent(String content, String key) {
    List<Integer> list = new ArrayList<>();
    if (content == null || content.isBlank())
      return list;
    Matcher m = Pattern.compile(key + "\\s*[:=]\\s*\\[([^\\]]*)\\]", Pattern.DOTALL).matcher(content);
    if (m.find()) {
      String inner = m.group(1);
      for (String part : inner.split(",")) {
        try {
          list.add(Integer.parseInt(part.trim()));
        } catch (Exception e) {
          // ignore
        }
      }
    }
    return list;
  }

  private static List<Map<String, String>> getNestedObjectsAsMaps(String content, String key) {
    List<Map<String, String>> result = new ArrayList<>();
    if (content == null || content.isBlank())
      return result;
    Matcher m = Pattern.compile(key + "\\s*[:=]\\s*\\[([^\\]]*)\\]", Pattern.DOTALL).matcher(content);
    if (m.find()) {
      String inner = m.group(1).trim();
      // split by '},{' roughly
      String[] objs = inner.split("\\},\\s*\\{");
      for (String o : objs) {
        String obj = o.trim();
        if (!obj.startsWith("{"))
          obj = "{" + obj;
        if (!obj.endsWith("}"))
          obj = obj + "}";
        // remove surrounding braces
        String body = obj.substring(1, obj.length() - 1);
        Map<String, String> props = new HashMap<>();
        Matcher pm = Pattern.compile(
            "(\\w+)\\s*[:=]\\s*('(?:[^']*)'|\"(?:[^\"]*)\"|\\[[^\\]]*\\]|\\{[^}]*\\}|[^,\\n]+)",
            Pattern.DOTALL)
            .matcher(body);
        while (pm.find()) {
          String k = pm.group(1);
          String v = pm.group(2).trim();
          props.put(k, unquote(v));
        }
        result.add(props);
      }
    }
    return result;
  }

  private static Map<String, String> parseSubObject(String content, String key) {
    Map<String, String> res = new HashMap<>();
    if (content == null || content.isBlank())
      return res;
    Matcher m = Pattern.compile(key + "\\s*[:=]\\s*\\{([^}]*)\\}", Pattern.DOTALL).matcher(content);
    if (m.find()) {
      String inner = m.group(1);
      Matcher pm = Pattern.compile("(\\w+)\\s*[:=]\\s*('(?:[^']*)'|\"(?:[^\"]*)\"|[^,\\n]+)", Pattern.DOTALL)
          .matcher(inner);
      while (pm.find()) {
        res.put(pm.group(1), unquote(pm.group(2).trim()));
      }
    }
    return res;
  }

  private static String unquote(String value) {
    if (value == null)
      return null;
    value = value.trim();
    if ((value.startsWith("'") && value.endsWith("'"))
        || (value.startsWith("\"") && value.endsWith("\""))) {
      return value.substring(1, value.length() - 1);
    }
    return value;
  }

  private static String getString(Map<String, String> props, String key) {
    if (props == null)
      return "";
    String v = props.get(key);
    return v == null ? "" : unquote(v.trim());
  }

  private static Integer getInteger(Map<String, String> props, String key) {
    if (props == null)
      return null;
    String v = props.get(key);
    if (v == null)
      return null;
    try {
      return Integer.valueOf(v.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static List<String> getList(Map<String, String> props, String key) {
    if (props == null)
      return Collections.emptyList();
    String v = props.get(key);
    if (v == null)
      return Collections.emptyList();
    v = v.trim();
    if (v.startsWith("[") && v.endsWith("]")) {
      String inner = v.substring(1, v.length() - 1).trim();
      if (inner.isBlank())
        return Collections.emptyList();
      String[] parts = inner.split(",");
      List<String> res = new ArrayList<>();
      for (String p : parts) {
        res.add(unquote(p.trim()));
      }
      return res;
    }
    return List.of(unquote(v));
  }

  private static Map<String, String> extractImages(Map<String, String> props) {
    Map<String, String> images = new HashMap<>();
    if (props == null)
      return images;
    for (var e : props.entrySet()) {
      String key = e.getKey();
      String val = e.getValue();
      if (key == null || val == null)
        continue;
      if (key.toLowerCase().contains("image")) {
        String v = val.trim();
        if (v.startsWith("{") && v.endsWith("}")) {
          String inner = v.substring(1, v.length() - 1);
          Matcher pm = Pattern.compile("(\\w+)\\s*[:=]\\s*('(?:[^']*)'|\"(?:[^\"]*)\")").matcher(inner);
          while (pm.find()) {
            images.put(pm.group(1), unquote(pm.group(2)));
          }
        } else {
          images.put(key, unquote(v));
        }
      }
    }
    return images;
  }

  private static Optional<Path> findFile(Path dir, String filename) {
    try {
      Path candidate = dir.resolve(filename);
      if (Files.exists(candidate) && Files.isRegularFile(candidate)) {
        return Optional.of(candidate);
      }
    } catch (Exception e) {
      // ignore
    }
    return Optional.empty();
  }

  private static String readFileSafe(Path p) {
    try {
      return Files.readString(p, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read file: " + p, e);
    }
  }

  private static Map<String, String> parseProperties(String content) {
    Map<String, String> props = new HashMap<>();
    if (content == null || content.isBlank())
      return props;
    Matcher m = PROPERTY_SIMPLE.matcher(content);
    while (m.find()) {
      String key = m.group(1).trim();
      String value = m.group(2).trim();
      props.put(key, value);
    }
    return props;
  }

  private static Optional<String> extractIdFromContent(String content) {
    if (content == null || content.isBlank())
      return Optional.empty();
    Matcher m = ID_SIMPLE.matcher(content);
    if (m.find())
      return Optional.ofNullable(m.group(1));
    return Optional.empty();
  }

  private static String stripExt(String filename) {
    int idx = filename.lastIndexOf('.');
    return idx > 0 ? filename.substring(0, idx) : filename;
  }

  private static String extractNameEn(String content) {
    if (content == null || content.isBlank())
      return null;
    Matcher m = NAME_EN_IN_OBJ.matcher(content);
    if (m.find())
      return m.group(1);
    m = NAME_SIMPLE.matcher(content);
    if (m.find())
      return m.group(1);
    return null;
  }
}
