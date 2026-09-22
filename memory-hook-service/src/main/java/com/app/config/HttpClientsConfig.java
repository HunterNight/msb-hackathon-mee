package com.app.config;

import com.app.constant.AppConstants;
import com.app.service.client.InternalTokenProvider;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.slf4j.MDC;
import org.springframework.web.client.RestClient;

/**
 * The only outbound call this service makes: the masked transcript from mi-assistant, with a
 * client-credentials token carrying scope {@code mi:transcript} (design §2 H4).
 */
@Configuration
public class HttpClientsConfig {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

  @Bean
  public RestClient miClient(
      @Value("${app.clients.mi}") String baseUrl, InternalTokenProvider tokens) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(CONNECT_TIMEOUT);
    factory.setReadTimeout(READ_TIMEOUT);
    return RestClient.builder()
        .baseUrl(baseUrl)
        .requestFactory(factory)
        .requestInitializer(
            request -> {
              request.getHeaders().setBearerAuth(tokens.token());
              request
                  .getHeaders()
                  .add(AppConstants.HDR_REQUEST_ID, MDC.get(AppConstants.MDC_REQUEST_ID));
              request.getHeaders().add(HttpHeaders.ACCEPT, "application/json");
            })
        .build();
  }
}
