package com.app.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Maps a Keycloak access token onto Spring authorities: realm roles become {@code ROLE_*} and the
 * {@code scope} claim becomes {@code SCOPE_*} (guideline 05 §2).
 */
public class KeycloakRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {

  private static final String CLAIM_REALM_ACCESS = "realm_access";
  private static final String CLAIM_ROLES = "roles";
  private static final String CLAIM_SCOPE = "scope";

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    Collection<GrantedAuthority> authorities = new ArrayList<>();

    Object realmAccess = jwt.getClaim(CLAIM_REALM_ACCESS);
    if (realmAccess instanceof Map<?, ?> map && map.get(CLAIM_ROLES) instanceof Collection<?> roles) {
      for (Object role : roles) {
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
      }
    }

    String scope = jwt.getClaimAsString(CLAIM_SCOPE);
    if (scope != null && !scope.isBlank()) {
      for (String value : scope.split(" ")) {
        if (!value.isBlank()) {
          authorities.add(new SimpleGrantedAuthority("SCOPE_" + value));
        }
      }
    }

    List<String> scopes = jwt.getClaimAsStringList(CLAIM_SCOPE);
    if (scopes != null) {
      for (String value : scopes) {
        authorities.add(new SimpleGrantedAuthority("SCOPE_" + value));
      }
    }

    return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
  }
}
