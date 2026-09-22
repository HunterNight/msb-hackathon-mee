package com.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * memory-hook-service — MSB DigiBank customer channel.
 *
 * <p>Package layout is fixed by guideline/01-backend-conventions.md §2:
 * {@code constant, config, controller, dto, exception, model, repository, service}.
 */
@SpringBootApplication
@EnableScheduling
public class Application {

  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}
