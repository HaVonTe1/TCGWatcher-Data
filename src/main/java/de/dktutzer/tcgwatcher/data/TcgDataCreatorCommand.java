package de.dktutzer.tcgwatcher.data;

import de.dktutzer.tcgwatcher.data.service.TCGMapperService;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

@ShellComponent
@RequiredArgsConstructor
public class TcgDataCreatorCommand {

  private final TCGMapperService tcgMapperService;

  @ShellMethod(key = "create-data", value = "Create TCG data")
  public String createData(
      @ShellOption(defaultValue = "spring") String arg
  ) {

    try {
      tcgMapperService.readFromSourceAndWriteToJson();
      tcgMapperService.readFromJsonAndWriteToSqlite();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return "Hello world " + arg;
  }
}
