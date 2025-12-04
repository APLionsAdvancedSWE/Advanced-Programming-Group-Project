package com.dev.tradingapi.config;

import com.dev.tradingapi.model.User;
import com.dev.tradingapi.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * Custom UserDetailsService that loads users from the database.
 */
@Service
public class DatabaseUserDetailsService implements UserDetailsService {

  private final UserRepository userRepository;

  @Autowired
  public DatabaseUserDetailsService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

    return org.springframework.security.core.userdetails.User.builder()
            .username(user.getUsername())
            .password(user.getPassword())
            .authorities(Collections.singletonList(
                    new SimpleGrantedAuthority("ROLE_" + user.getRole())))
            .build();
  }
}