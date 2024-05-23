package de.dktutzer.tcgwatcher.data.service;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.shell.interactive.enabled=false")
class TCGDataMapperServiceIT {

  @Autowired
  private TCGMapperService tcgMapperService;

  @Test
  void testRead() throws IOException {

    tcgMapperService.readFromSourceAndWriteToJson();

  }

  @Test
  void testDB() throws IOException {

    tcgMapperService.readFromJsonAndWriteToSqlite();

  }
}
