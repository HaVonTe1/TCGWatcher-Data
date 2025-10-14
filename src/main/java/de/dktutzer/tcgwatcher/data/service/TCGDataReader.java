/*
Reads Set Data from: https://github.com/PokemonTCG/pokemon-tcg-data
 */

package de.dktutzer.tcgwatcher.data.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dktutzer.tcgwatcher.data.data.model.TCGDataSetModel;
import de.dktutzer.tcgwatcher.data.data.model.TCGWatcherCardModel;
import de.dktutzer.tcgwatcher.data.data.model.TCGWatcherSeriesModel;
import de.dktutzer.tcgwatcher.data.data.model.TCGWatcherSetModel;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TCGDataReader {

  private final ObjectMapper objectMapper;

  public Map<String, TCGWatcherSetModel> readSetsFromFile() throws IOException {

    Map<String, TCGWatcherSetModel> result = new HashMap<>();
    var jsonString = readSetsFileIntoString();
    List<TCGDataSetModel> list = objectMapper.readValue(jsonString, new TypeReference<>(){});
    list.forEach(
        set -> {
          var names = Map.of("en", set.getName());
          var seriesMap = Map.of("en", set.getSeries());
          var tcgWatcherSetModel =
              TCGWatcherSetModel.builder()
                  .id(set.getId())
                  .code(set.getPtcgoCode())
                  .numberTotal(set.getTotal())
                  .numberOfficial(set.getPrintedTotal())
                  .abbreviation("")
                  .series( TCGWatcherSeriesModel.builder().id("").names(seriesMap).build())
                  .names(names)
                  .build();
          result.put(set.getId(), tcgWatcherSetModel);
        });

    return result;

  }

  public  String readSetsFileIntoString() throws IOException {
    StringBuilder stringBuilder = new StringBuilder();

      ZipFile zipFile = new ZipFile("src/main/resources/sets_en.zip");
      Enumeration<? extends ZipEntry> entries = zipFile.entries();

      while(entries.hasMoreElements()){
        ZipEntry entry = entries.nextElement();
        InputStream stream = zipFile.getInputStream(entry);

        Scanner s = new Scanner(stream).useDelimiter("\\A");
        String result = s.hasNext() ? s.next() : "";
        stringBuilder.append(result);
        stream.close();
      }
      zipFile.close();

    return stringBuilder.toString();
  }


}
