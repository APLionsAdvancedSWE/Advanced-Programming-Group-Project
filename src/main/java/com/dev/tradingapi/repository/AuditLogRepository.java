package com.dev.tradingapi.repository;

import com.dev.tradingapi.model.AuditLog;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for AuditLog entity.
 * Provides database access methods for audit logging.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

  /**
   * Find audit logs by API key.
   *
   * @param apiKey the API key to search for
   * @return list of audit logs for that API key
   */
  List<AuditLog> findByApiKey(String apiKey);

  /**
   * Find audit logs by request path.
   *
   * @param path the request path to search for
   * @return list of audit logs for that path
   */
  List<AuditLog> findByPath(String path);

  /**
   * Find audit logs within a time range.
   *
   * @param start start timestamp
   * @param end end timestamp
   * @return list of audit logs within the time range
   */
  List<AuditLog> findByTsBetween(Instant start, Instant end);
}