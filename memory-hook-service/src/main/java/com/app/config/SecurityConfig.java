package com.app.config;

import com.app.constant.AppConstants;
import com.app.constant.Routes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * There is no customer-facing route and no gateway route: every caller is a service presenting a
 * client-credentials token, and the scope it holds decides what it may do (design §4 "Access").
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(AppConstants.PUBLIC_PATHS)
                    .permitAll()
                    .requestMatchers(Routes.ADMIN + "/**")
                    .hasAuthority(Routes.SCOPE_ADMIN)
                    .requestMatchers(Routes.MEMORY + "/**")
                    .hasAnyAuthority(
                        Routes.SCOPE_READ,
                        Routes.SCOPE_WRITE_CHAT,
                        Routes.SCOPE_ERASE)
                    // Anything not named above is not reachable, including a stray actuator path.
                    .anyRequest()
                    .denyAll())
        .oauth2ResourceServer(
            oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(new KeycloakRoleConverter())));
    return http.build();
  }
}
