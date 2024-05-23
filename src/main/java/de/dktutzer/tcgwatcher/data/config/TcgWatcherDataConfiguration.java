package de.dktutzer.tcgwatcher.data.config;


import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

@Configuration
@Slf4j
public class TcgWatcherDataConfiguration {

  private final RestTemplateBuilder restTemplateBuilder;
  private final Environment env;

  public TcgWatcherDataConfiguration(RestTemplateBuilder restTemplateBuilder, Environment env) {
    this.restTemplateBuilder = restTemplateBuilder;
    this.env = env;
  }
  @Bean
  public RestTemplate restTemplate() {
    return restTemplateBuilder.errorHandler(new ResponseErrorHandler() {
      @Override
      public boolean hasError(ClientHttpResponse response) throws IOException {
        var statusCode = response.getStatusCode();
        return !statusCode.is2xxSuccessful();
      }

      @Override
      public void handleError(ClientHttpResponse response) throws IOException {
        var statusCode = response.getStatusCode();


        log.warn("StatusCode: {}", statusCode.value());
      }
    }).build();

  }


//  @Bean
//  public DataSource dataSource() {
//    final DriverManagerDataSource dataSource = new DriverManagerDataSource();
//    dataSource.setDriverClassName(env.getProperty("driverClassName"));
//    dataSource.setUrl(env.getProperty("url"));
//    dataSource.setUsername(env.getProperty("user"));
//    dataSource.setPassword(env.getProperty("password"));
//    return dataSource;
//  }

}
