package com.app.config;

import com.app.constant.AppConstants;
import com.app.constant.ErrorCode;
import com.app.exception.BusinessException;
import java.util.UUID;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves {@link CurrentCustomer}. For customer tokens the id is the {@code customerId} claim.
 * For internal service-to-service calls acting on behalf of a customer the id comes from
 * {@code X-Customer-Id}, which the gateway strips from inbound public traffic.
 */
public class CurrentCustomerArgumentResolver implements HandlerMethodArgumentResolver {

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return parameter.hasParameterAnnotation(CurrentCustomer.class)
        && UUID.class.equals(parameter.getParameterType());
  }

  @Override
  public Object resolveArgument(
      MethodParameter parameter,
      ModelAndViewContainer container,
      NativeWebRequest request,
      WebDataBinderFactory binderFactory) {

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof JwtAuthenticationToken token) {
      Jwt jwt = token.getToken();
      String claim = jwt.getClaimAsString(AppConstants.CLAIM_CUSTOMER_ID);
      if (claim != null && !claim.isBlank()) {
        return UUID.fromString(claim);
      }
    }

    String header = request.getHeader(AppConstants.HDR_CUSTOMER_ID);
    if (header != null && !header.isBlank()) {
      return UUID.fromString(header);
    }
    throw new BusinessException(ErrorCode.UNAUTHORIZED);
  }
}
