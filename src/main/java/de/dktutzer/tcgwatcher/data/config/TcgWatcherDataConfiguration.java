package de.dktutzer.tcgwatcher.data.config;

import java.io.IOException;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.restclient.RestTemplateBuilder;

@Configuration
public class TcgWatcherDataConfiguration {

  private static final Logger log = LoggerFactory.getLogger(TcgWatcherDataConfiguration.class);

  private final RestTemplateBuilder restTemplateBuilder;

  public TcgWatcherDataConfiguration(RestTemplateBuilder restTemplateBuilder) {
    this.restTemplateBuilder = restTemplateBuilder;
  }

  @Bean
  public RestTemplate restTemplate() {
    return restTemplateBuilder
        .errorHandler(
            new ResponseErrorHandler() {
              @Override
              public boolean hasError(ClientHttpResponse response) throws IOException {
                return response.getStatusCode().isError();
              }

              @Override
              public void handleError(URI url, HttpMethod method, ClientHttpResponse response) throws IOException {
                log.error("RestTemplate error: {}", response.getStatusCode());
                throw new HttpStatusCodeException(response.getStatusCode()) {};
              }

            })
        .build();
  }
}
