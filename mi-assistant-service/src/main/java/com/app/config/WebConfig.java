package com.app.config;

import com.app.constant.AppConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** MVC wiring: {@link CurrentCustomer} resolution and request-id propagation into logs. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(new CurrentCustomerArgumentResolver());
  }

  @Bean
  public FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
    FilterRegistrationBean<RequestIdFilter> registration =
        new FilterRegistrationBean<>(new RequestIdFilter());
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return registration;
  }

  /**
   * Puts {@code X-Request-Id} into the MDC so every JSON log line and the response envelope carry
   * the same correlation id (guideline 00 §4).
   */
  public static class RequestIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {

      String requestId = request.getHeader(AppConstants.HDR_REQUEST_ID);
      if (requestId == null || requestId.isBlank()) {
        requestId = com.app.model.BaseEntity.newId().toString();
      }
      MDC.put(AppConstants.MDC_REQUEST_ID, requestId);
      response.setHeader(AppConstants.HDR_REQUEST_ID, requestId);
      try {
        chain.doFilter(request, response);
      } finally {
        MDC.remove(AppConstants.MDC_REQUEST_ID);
      }
    }
  }
}
