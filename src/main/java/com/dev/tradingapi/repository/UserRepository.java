package com.dev.tradingapi.repository;

import com.dev.tradingapi.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

/**
 * Repository for User entity.
 * Provides database access for authentication and user management.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

  /**
   * Find user by username.
   *
   * @param username the username to search for
   * @return Optional containing the user if found
   */
  Optional<User> findByUsername(String username);
}