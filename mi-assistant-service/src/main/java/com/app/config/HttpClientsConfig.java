package com.app.config;

import com.app.constant.AppConstants;
import com.app.constant.MiConstants;
import com.app.service.client.InternalTokenProvider;
import com.app.service.client.PeerClients.AccountClient;
import com.app.service.client.PeerClients.AuthClient;
import com.app.service.client.PeerClients.BillClient;
import com.app.service.client.PeerClients.CardClient;
import com.app.service.client.PeerClients.LendingClient;
import com.app.service.client.PeerClients.MemoryClient;
import com.app.service.client.PeerClients.NotificationClient;
import com.app.service.client.PeerClients.SavingClient;
import com.app.service.client.PeerClients.TransferClient;
import java.time.Duration;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/** Peer clients. Every internal call carries a client-credentials token and the correlation id. */
@Configuration
public class HttpClientsConfig {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(1);
  private static final Duration PEER_TIMEOUT = Duration.ofSeconds(5);
  /** A quote is on the customer's critical path, so it gets a tighter budget than a write. */
  private static final Duration QUOTE_TIMEOUT = Duration.ofSeconds(3);

  /**
   * The VRM agent answers with an LLM call, which the hosted model serves in 4-14 seconds, so the
   * peer timeout would cut off nearly every answer. Still bounded well under the client's own patience
   * because a slow answer that arrives is worse than a fast fallback: mi composes its own reply the
   * moment this expires.
   */
  private static final Duration VRM_TIMEOUT = Duration.ofSeconds(20);

  private final InternalTokenProvider tokenProvider;

  public HttpClientsConfig(InternalTokenProvider tokenProvider) {
    this.tokenProvider = tokenProvider;
  }

  /** Plain RestClient, not an interface proxy: the agent has one endpoint and a hand-built body. */
  @Bean
  public RestClient vrmClient(
      RestClient.Builder builder, @Value("${app.mi.vrm.base-url:http://localhost:8099}") String baseUrl) {
    return client(builder, baseUrl, VRM_TIMEOUT);
  }

  @Bean
  public RestClient cmsClient(
      RestClient.Builder builder, @Value("${app.clients.cms}") String baseUrl) {
    return client(builder, baseUrl, PEER_TIMEOUT);
  }

  @Bean
  public AuthClient authClient(
      RestClient.Builder builder, @Value("${app.clients.auth}") String baseUrl) {
    return proxy(client(builder, baseUrl, QUOTE_TIMEOUT), AuthClient.class);
  }

  @Bean
  public AccountClient accountClient(
      RestClient.Builder builder, @Value("${app.clients.account}") String baseUrl) {
    return proxy(client(builder, baseUrl, PEER_TIMEOUT), AccountClient.class);
  }

  @Bean
  public TransferClient transferClient(
      RestClient.Builder builder, @Value("${app.clients.transfer}") String baseUrl) {
    return proxy(client(builder, baseUrl, PEER_TIMEOUT), TransferClient.class);
  }

  @Bean
  public BillClient billClient(
      RestClient.Builder builder, @Value("${app.clients.bill}") String baseUrl) {
    return proxy(client(builder, baseUrl, PEER_TIMEOUT), BillClient.class);
  }

  @Bean
  public SavingClient savingClient(
      RestClient.Builder builder, @Value("${app.clients.saving}") String baseUrl) {
    return proxy(client(builder, baseUrl, PEER_TIMEOUT), SavingClient.class);
  }

  @Bean
  public CardClient cardClient(
      RestClient.Builder builder, @Value("${app.clients.card}") String baseUrl) {
    return proxy(client(builder, baseUrl, PEER_TIMEOUT), CardClient.class);
  }

  @Bean
  public LendingClient lendingClient(
      RestClient.Builder builder, @Value("${app.clients.lending}") String baseUrl) {
    return proxy(client(builder, baseUrl, PEER_TIMEOUT), LendingClient.class);
  }

  @Bean
  public NotificationClient notificationClient(
      RestClient.Builder builder, @Value("${app.clients.notification}") String baseUrl) {
    return proxy(client(builder, baseUrl, PEER_TIMEOUT), NotificationClient.class);
  }

  @Bean
  public MemoryClient memoryClient(
      RestClient.Builder builder, @Value("${app.clients.memory}") String baseUrl) {
    return proxy(
        client(builder, baseUrl, Duration.ofMillis(MiConstants.INSIGHT_TIMEOUT_MS * 4L)),
        MemoryClient.class);
  }

  private RestClient client(RestClient.Builder builder, String baseUrl, Duration readTimeout) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(CONNECT_TIMEOUT);
    factory.setReadTimeout(readTimeout);

    return builder
        .clone()
        .baseUrl(baseUrl)
        .requestFactory(factory)
        .requestInterceptor(
            (request, body, execution) -> {
              HttpHeaders headers = request.getHeaders();
              headers.setBearerAuth(tokenProvider.token());
              set(headers, AppConstants.HDR_REQUEST_ID, MDC.get(AppConstants.MDC_REQUEST_ID));
              forward(headers, AppConstants.HDR_CUSTOMER_ID);
              forward(headers, HttpHeaders.ACCEPT_LANGUAGE);
              return execution.execute(request, body);
            })
        .build();
  }

  private void set(HttpHeaders headers, String name, String value) {
    if (value != null && !value.isBlank() && !headers.containsHeader(name)) {
      headers.set(name, value);
    }
  }

  private void forward(HttpHeaders headers, String name) {
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
      set(headers, name, attributes.getRequest().getHeader(name));
    }
  }

  private <T> T proxy(RestClient restClient, Class<T> type) {
    return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
        .build()
        .createClient(type);
  }
}
