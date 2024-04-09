package de.dktutzer.tcgwatcher.data.config;


import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

@Configuration
@Slf4j
public class TcgWatcherDataConfiguration {

  private final RestTemplateBuilder restTemplateBuilder;

  public TcgWatcherDataConfiguration(RestTemplateBuilder restTemplateBuilder) {
    this.restTemplateBuilder = restTemplateBuilder;
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
//  public ObjectMapper objectMapper() {
//    return new ObjectMapper();
//  }

}
