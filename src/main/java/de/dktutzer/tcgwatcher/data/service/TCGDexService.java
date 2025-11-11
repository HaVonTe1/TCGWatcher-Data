package de.dktutzer.tcgwatcher.data.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.dktutzer.tcgwatcher.data.data.model.DexCardData;
import de.dktutzer.tcgwatcher.data.data.model.DexSetData;
import de.dktutzer.tcgwatcher.data.data.model.DexSeriesData;

/**
 * Utility to read a pokeapi TypeScript data dump and produce a nested map structure:
 * Series -> Sets -> Cards
 */
public class TCGDexService {

  private static final Pattern NAME_SIMPLE = Pattern.compile("name\\s*[:=]\\s*['\"]([^'\"]+)['\"]");
  private static final Pattern NAME_EN_IN_OBJ = Pattern.compile("name\\s*[:=]\\s*\\{[^}]*en\\s*[:=]\\s*['\"]([^'\"]+)['\"]", Pattern.DOTALL);
  private static final Pattern ID_SIMPLE = Pattern.compile("id\\s*[:=]\\s*['\"]([^'\"]+)['\"]");
  private static final Pattern PROPERTY_SIMPLE = Pattern.compile("(\\w+)\\s*[:=]\\s*(\\{[^}]*\\}|\\[[^\\]]*\\]|'[^']*'|\"[^\"]*\"|[^,\\n]+)", Pattern.DOTALL);

  private TCGDexService() {
  }

  /**
   * Read the given base directory and build a map of series -> sets -> cards.
   *
   * @param baseDirPath path to the base data directory (e.g. /.../pokeapi/cards-database/data)
   * @return map keyed by series id or folder name
   * @throws IOException on IO errors
   */
  public static Map<String, DexSeriesData> readAllSeries(String baseDirPath) throws IOException {
    Path base = Paths.get(baseDirPath);
    if (!Files.isDirectory(base)) {
      return Collections.emptyMap();
    }

    Map<String, DexSeriesData> seriesMap = new HashMap<>();
    Map<String, Map<String, String>> seriesPropsMap = new HashMap<>();

    // First, read all .ts files in the base dir - these usually contain series metadata
    try (var stream = Files.list(base)) {
      List<Path> entries = stream.toList();
      // process files to gather series metadata
      for (Path p : entries) {
        if (Files.isRegularFile(p) && p.toString().endsWith(".ts")) {
          String content = readFileSafe(p);
          String id = extractIdFromContent(content).orElse(stripExt(p.getFileName().toString()));
          String nameStr = Optional.ofNullable(extractNameEn(content)).orElse(stripExt(p.getFileName().toString()));
          Map<String,String> nameMap = extractLocalizedMap(content, "name");
          Map<String, String> props = parseProperties(content);
          // initialize series with empty sets; sets will be filled from subfolders
          seriesMap.putIfAbsent(id, new DexSeriesData(id, nameMap.isEmpty() ? Map.of("en", nameStr) : nameMap, new HashMap<>()));
          seriesPropsMap.putIfAbsent(id, props);
        }
      }

      // process directories - each directory that matches a series name contains sets
      for (Path p : entries) {
        if (Files.isDirectory(p)) {
          String seriesFolderName = p.getFileName().toString();
          // determine series id: prefer existing seriesMap key that equals folder name, otherwise use folder name
          DexSeriesData series = seriesMap.getOrDefault(seriesFolderName, new DexSeriesData(seriesFolderName, Map.of("en", seriesFolderName), new HashMap<>()));

          // scan sets inside series folder
          Map<String, DexSetData> sets = series.sets();
          try (var setStream = Files.list(p)) {
            for (Path setPath : setStream.toList()) {
              if (Files.isDirectory(setPath)) {
                String setId = setPath.getFileName().toString();
                // attempt to find metadata file for set (index.ts or <setId>.ts)
                String setName = setId;
                String setRaw = "";
                Map<String, String> setProps = Map.of();
                Optional<Path> maybeMeta = findFile(setPath, setId + ".ts").or(() -> findFile(setPath, "index.ts"));
                if (maybeMeta.isPresent()) {
                  String setContent = readFileSafe(maybeMeta.get());
                  setName = Optional.ofNullable(extractNameEn(setContent)).orElse(setName);
                  setProps = parseProperties(setContent);
                  setRaw = setContent;
                }

                Map<String, DexCardData> cards = new HashMap<>();
                // read .ts files inside set folder (cards)
                try (var cardStream = Files.list(setPath)) {
                  for (Path cardFile : cardStream.toList()) {
                    if (Files.isRegularFile(cardFile) && cardFile.toString().endsWith(".ts")) {
                      String cardContent = readFileSafe(cardFile);
                      String cardId = extractIdFromContent(cardContent).orElse(stripExt(cardFile.getFileName().toString()));
                      String cardName = Optional.ofNullable(extractNameEn(cardContent)).orElse(stripExt(cardFile.getFileName().toString()));
                      Map<String, String> cardProps = parseProperties(cardContent);

                      Map<String,String> cardNames = extractLocalizedMap(cardContent, "name");
                      List<Integer> dexId = getIntegerListFromContent(cardContent, "dexId");
                      Map<String,String> evolveFrom = extractLocalizedMap(cardContent, "evolveFrom");
                      if (evolveFrom.isEmpty()) evolveFrom = extractLocalizedMap(cardContent, "evolvesFrom");
                      Map<String,String> description = extractLocalizedMap(cardContent, "description");
                      Map<String,String> thirdParty = parseSubObject(cardContent, "thirdParty");
                      List<Map<String,String>> abilities = getNestedObjectsAsMaps(cardContent, "abilities");
                      List<Map<String,String>> attacks = getNestedObjectsAsMaps(cardContent, "attacks");
                      List<Map<String,String>> weaknessesObj = getNestedObjectsAsMaps(cardContent, "weaknesses");
                      List<Map<String,String>> resistancesObj = getNestedObjectsAsMaps(cardContent, "resistances");
                      Integer retreat = getInteger(cardProps, "retreat");

                      // build typed fields from props
                      String number = getString(cardProps, "number");
                      String supertype = getString(cardProps, "supertype");
                      List<String> subtypes = getList(cardProps, "subtypes");
                      String rarity = getString(cardProps, "rarity");
                      String hp = getString(cardProps, "hp");
                      List<String> types = getList(cardProps, "types");
                      String stage = getString(cardProps, "stage");
                      List<String> retreatCost = getList(cardProps, "retreatCost");
                      Integer convertedRetreatCost = getInteger(cardProps, "convertedRetreatCost");
                      String artist = getString(cardProps, "illustrator");
                      Map<String,String> thirdPartyProps = parseSubObject(cardContent, "thirdParty");
                      String category = getString(cardProps, "category");

                      cards.put(
                          cardId,
                          new DexCardData(
                              cardId,
                              cardNames,
                              number,
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
                  }
                }

                // attempt to fill set images map from setProps
                Map<String, String> images = extractImages(setProps);
                Integer printedTotal = getInteger(setProps, "printedTotal");
                Integer total = getInteger(setProps, "total");
                String seriesIdRef = getString(setProps, "series");
                String seriesNameRef = getString(setProps, "seriesName");
                String releaseDate = getString(setProps, "releaseDate");
                String ptcgoCode = getString(setProps, "ptcgoCode");

                Map<String,String> nameMap = extractLocalizedMap(setRaw, "name");
                Map<String,String> abbreviations = parseSubObject(setRaw, "abbreviations");
                Map<String,String> thirdPartySet = parseSubObject(setRaw, "thirdParty");
                sets.put(
                    setId,
                    new DexSetData(
                        setId,
                        nameMap.isEmpty() ? Map.of("en", setName) : nameMap,
                        seriesIdRef,
                        printedTotal,
                        total,
                        releaseDate,
                        ptcgoCode,
                        abbreviations,
                        thirdPartySet,
                        images,
                        cards));
               }
             }
           }

          Map<String,String> seriesNameMap = extractLocalizedMap(seriesPropsMap.getOrDefault(series.id(), Map.of()).toString(), "name");
          String fallbackSeriesEn = series.name() != null && series.name().get("en") != null ? series.name().get("en") : series.id();
          seriesMap.put(series.id(), new DexSeriesData(series.id(), seriesNameMap.isEmpty() ? Map.of("en", fallbackSeriesEn) : seriesNameMap, sets));
         }
       }
     }

     return seriesMap;
   }

  // --- helpers to extract structured pieces

  private static Map<String,String> extractLocalizedMap(String content, String key) {
    Map<String,String> result = new HashMap<>();
    if (content == null || content.isBlank()) return result;
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
    if (content == null || content.isBlank()) return list;
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

  private static List<Map<String,String>> getNestedObjectsAsMaps(String content, String key) {
    List<Map<String,String>> result = new ArrayList<>();
    if (content == null || content.isBlank()) return result;
    Matcher m = Pattern.compile(key + "\\s*[:=]\\s*\\[([^\\]]*)\\]", Pattern.DOTALL).matcher(content);
    if (m.find()) {
      String inner = m.group(1).trim();
      // split by '},{' roughly
      String[] objs = inner.split("\\},\\s*\\{");
      for (String o : objs) {
        String obj = o.trim();
        if (!obj.startsWith("{")) obj = "{" + obj;
        if (!obj.endsWith("}")) obj = obj + "}";
        // remove surrounding braces
        String body = obj.substring(1, obj.length()-1);
        Map<String,String> props = new HashMap<>();
        Matcher pm = Pattern.compile("(\\w+)\\s*[:=]\\s*('(?:[^']*)'|\"(?:[^\"]*)\"|\\[[^\\]]*\\]|\\{[^}]*\\}|[^,\\n]+)", Pattern.DOTALL).matcher(body);
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

  private static Map<String,String> parseSubObject(String content, String key) {
    Map<String,String> res = new HashMap<>();
    if (content == null || content.isBlank()) return res;
    Matcher m = Pattern.compile(key + "\\s*[:=]\\s*\\{([^}]*)\\}", Pattern.DOTALL).matcher(content);
    if (m.find()) {
      String inner = m.group(1);
      Matcher pm = Pattern.compile("(\\w+)\\s*[:=]\\s*('(?:[^']*)'|\"(?:[^\"]*)\"|[^,\\n]+)", Pattern.DOTALL).matcher(inner);
      while (pm.find()) {
        res.put(pm.group(1), unquote(pm.group(2).trim()));
      }
    }
    return res;
  }

  private static String unquote(String value) {
    if (value == null) return null;
    value = value.trim();
    if ((value.startsWith("'") && value.endsWith("'")) || (value.startsWith("\"") && value.endsWith("\""))) {
      return value.substring(1, value.length() - 1);
    }
    return value;
  }

  private static String getString(Map<String, String> props, String key) {
    if (props == null) return "";
    String v = props.get(key);
    return v == null ? "" : unquote(v.trim());
  }

  private static Integer getInteger(Map<String, String> props, String key) {
    if (props == null) return null;
    String v = props.get(key);
    if (v == null) return null;
    try {
      return Integer.valueOf(v.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static List<String> getList(Map<String, String> props, String key) {
    if (props == null) return Collections.emptyList();
    String v = props.get(key);
    if (v == null) return Collections.emptyList();
    v = v.trim();
    if (v.startsWith("[") && v.endsWith("]")) {
      String inner = v.substring(1, v.length() - 1).trim();
      if (inner.isBlank()) return Collections.emptyList();
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
    if (props == null) return images;
    for (var e : props.entrySet()) {
      String key = e.getKey();
      String val = e.getValue();
      if (key == null || val == null) continue;
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
      return "";
    }
  }

  private static Map<String, String> parseProperties(String content) {
    Map<String, String> props = new HashMap<>();
    if (content == null || content.isBlank()) return props;
    Matcher m = PROPERTY_SIMPLE.matcher(content);
    while (m.find()) {
      String key = m.group(1).trim();
      String value = m.group(2).trim();
      props.put(key, value);
    }
    return props;
  }

  private static Optional<String> extractIdFromContent(String content) {
    if (content == null || content.isBlank()) return Optional.empty();
    Matcher m = ID_SIMPLE.matcher(content);
    if (m.find()) return Optional.ofNullable(m.group(1));
    return Optional.empty();
  }

  private static String stripExt(String filename) {
    int idx = filename.lastIndexOf('.');
    return idx > 0 ? filename.substring(0, idx) : filename;
  }

  private static String extractNameEn(String content) {
    if (content == null || content.isBlank()) return null;
    Matcher m = NAME_EN_IN_OBJ.matcher(content);
    if (m.find()) return m.group(1);
    m = NAME_SIMPLE.matcher(content);
    if (m.find()) return m.group(1);
    return null;
  }

}
