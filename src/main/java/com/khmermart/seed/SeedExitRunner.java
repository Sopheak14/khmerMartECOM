package com.khmermart.seed;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Used by scripts/reset_demo.sh: after Flyway has migrated and {@link DataSeeder} has committed,
 * shut the application down gracefully so the script gets a one-shot "migrate + seed" run.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnProperty(name = "app.seed.exit-after", havingValue = "true")
public class SeedExitRunner implements CommandLineRunner {

  private final ApplicationContext context;

  public SeedExitRunner(ApplicationContext context) {
    this.context = context;
  }

  @Override
  public void run(String... args) {
    System.exit(SpringApplication.exit(context, () -> 0));
  }
}
