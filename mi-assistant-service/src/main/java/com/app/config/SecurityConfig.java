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
 * Customer routes need the customer role; internal routes name their scope on the method. The
 * gateway already blocks {@code /internal/**} from the outside (guideline 05 §2).
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
                    .requestMatchers(Routes.MI + "/**")
                    .hasRole("customer")
                    .requestMatchers(AppConstants.INTERNAL + "/**")
                    .authenticated()
                    .anyRequest()
                    .denyAll())
        .oauth2ResourceServer(
            oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(new KeycloakRoleConverter())));
    return http.build();
  }
}
