package com.app.config;

import com.app.constant.AppConstants;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * {@code Accept-Language} drives every user-facing string; {@code vi} is the default and
 * {@code en} the only alternative (guideline 01 §6).
 */
@Configuration
public class I18nConfig {

  @Bean
  public LocaleResolver localeResolver() {
    AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
    resolver.setDefaultLocale(AppConstants.DEFAULT_LOCALE);
    resolver.setSupportedLocales(AppConstants.SUPPORTED_LOCALES);
    return resolver;
  }

  @Bean
  public MessageSource messageSource() {
    ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
    source.setBasename("classpath:i18n/messages");
    source.setDefaultEncoding("UTF-8");
    source.setFallbackToSystemLocale(false);
    source.setDefaultLocale(AppConstants.DEFAULT_LOCALE);
    source.setUseCodeAsDefaultMessage(true);
    return source;
  }

  /** Bean Validation messages such as {@code {transfer.validation.amountMin}} resolve here too. */
  @Bean
  public LocalValidatorFactoryBean validator(MessageSource messageSource) {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.setValidationMessageSource(messageSource);
    return validator;
  }
}
