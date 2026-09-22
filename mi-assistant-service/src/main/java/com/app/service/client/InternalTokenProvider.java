package com.app.service.client;

import com.app.constant.AppConstants;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Client-credentials token for {@code /internal/**} calls to peers. Tokens live 5 minutes and are
 * cached in memory per service instance (guideline 05 §1).
 */
@Component
public class InternalTokenProvider {

  private record CachedToken(String value, Instant expiresAt) {}

  private final RestClient tokenClient;
  private final String clientId;
  private final String clientSecret;
  private final AtomicReference<CachedToken> cache = new AtomicReference<>();

  public InternalTokenProvider(
      RestClient.Builder builder,
      @Value("${app.keycloak.token-uri}") String tokenUri,
      @Value("${app.keycloak.client-id}") String clientId,
      @Value("${app.keycloak.client-secret}") String clientSecret) {
    this.tokenClient = builder.baseUrl(tokenUri).build();
    this.clientId = clientId;
    this.clientSecret = clientSecret;
  }

  public String token() {
    CachedToken cached = cache.get();
    if (cached != null && cached.expiresAt().isAfter(Instant.now().plusSeconds(30))) {
      return cached.value();
    }
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "client_credentials");
    form.add("client_id", clientId);
    form.add("client_secret", clientSecret);

    @SuppressWarnings("unchecked")
    Map<String, Object> response =
        tokenClient
            .post()
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(Map.class);

    if (response == null || response.get("access_token") == null) {
      throw new IllegalStateException("Keycloak returned no access token for " + clientId);
    }
    String accessToken = String.valueOf(response.get("access_token"));
    long expiresIn =
        response.get("expires_in") instanceof Number number
            ? number.longValue()
            : AppConstants.SERVICE_TOKEN_TTL_SECONDS;
    cache.set(new CachedToken(accessToken, Instant.now().plus(Duration.ofSeconds(expiresIn))));
    return accessToken;
  }
}
