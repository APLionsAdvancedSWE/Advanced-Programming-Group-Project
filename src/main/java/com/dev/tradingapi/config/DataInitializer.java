package com.dev.tradingapi.config;

import com.dev.tradingapi.model.User;
import com.dev.tradingapi.repository.UserRepository;
import java.util.UUID;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Initializes the database with demo users on application startup.
 */
@Configuration
public class DataInitializer {

  /**
   * Creates demo users in the database on application startup.
   */
  @Bean
  public CommandLineRunner initUsers(UserRepository userRepository,
                                     PasswordEncoder passwordEncoder) {
    return args -> {
      // Only initialize if users table is empty
      if (userRepository.count() > 0) {
        System.out.println("Users already initialized, skipping...");
        return;
      }

      System.out.println("Initializing demo users...");

      // SEC Investigator
      User secInvestigator = new User(
              "sec-investigator",
              passwordEncoder.encode("sec-pass"),
              "SEC_INVESTIGATOR",
              null // No linked account
      );
      userRepository.save(secInvestigator);

      // Admin
      User admin = new User(
              "admin",
              passwordEncoder.encode("admin-pass"),
              "ADMIN",
              null // No linked account
      );
      userRepository.save(admin);

      // Trader 1 (linked to account aaaa1111)
      User trader1 = new User(
              "trader-john",
              passwordEncoder.encode("john-pass"),
              "TRADER",
              UUID.fromString("aaaa1111-1111-1111-1111-111111111111")
      );
      userRepository.save(trader1);

      // Trader 2 (linked to account bbbb2222)
      User trader2 = new User(
              "trader-jane",
              passwordEncoder.encode("jane-pass"),
              "TRADER",
              UUID.fromString("bbbb2222-2222-2222-2222-222222222222")
      );
      userRepository.save(trader2);

      System.out.println("✓ Created 4 demo users:");
      System.out.println("  - sec-investigator (SEC_INVESTIGATOR)");
      System.out.println("  - admin (ADMIN)");
      System.out.println("  - trader-john (TRADER → account aaaa1111)");
      System.out.println("  - trader-jane (TRADER → account bbbb2222)");
    };
  }
}