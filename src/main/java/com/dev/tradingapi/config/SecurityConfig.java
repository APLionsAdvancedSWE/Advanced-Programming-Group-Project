package com.dev.tradingapi.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for audit log access control.
 * Restricts audit endpoints to SEC investigators and administrators only.
 * Loads users from database.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final DatabaseUserDetailsService userDetailsService;

  @Autowired
  public SecurityConfig(DatabaseUserDetailsService userDetailsService) {
    this.userDetailsService = userDetailsService;
  }

  /**
   * Configures the main Spring Security filter chain for the application.
   *
   * @param http the HTTP security builder
   * @return the configured security filter chain
   * @throws Exception if a security configuration error occurs
   */
  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
            .csrf(csrf -> csrf.disable()) // Disable CSRF for REST API
            .authorizeHttpRequests(auth -> auth
                    // Audit endpoints require SEC_INVESTIGATOR or ADMIN role
                    .requestMatchers("/audit/**").hasAnyRole("SEC_INVESTIGATOR", "ADMIN")
                    // All other endpoints are accessible without authentication
                    .anyRequest().permitAll()
            )
            .httpBasic() // Use HTTP Basic Authentication
            .and()
            .userDetailsService(userDetailsService); // Use database user details service

    return http.build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}