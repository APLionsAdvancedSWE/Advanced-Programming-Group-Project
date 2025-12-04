package com.dev.tradingapi.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dev.tradingapi.model.AuditLog;
import com.dev.tradingapi.repository.AuditLogRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for audit logging system.
 * Tests the complete flow: Filter -> Service -> Repository.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private AuditLogRepository auditLogRepository;

  /**
   * Integration test: AuditLoggingFilter captures request and AuditService persists to database.
   *
   * <p>Integrates: AuditLoggingFilter -> AuditService -> AuditLogRepository -> H2 Database
   */
  @Test
  void testFilterToServiceToDatabaseIntegration() throws Exception {
    long initialCount = auditLogRepository.count();

    // Make request through full stack with accountId
    UUID testAccountId = UUID.fromString("aaaa1111-1111-1111-1111-111111111111");
    mockMvc.perform(get("/health")
                    .header("X-Account-Id", testAccountId.toString()))
            .andExpect(status().isOk());

    // Verify audit log was persisted
    long finalCount = auditLogRepository.count();
    assertEquals(initialCount + 1, finalCount,
            "Audit log should be persisted after request");

    // Get the most recent audit log
    List<AuditLog> allLogs = auditLogRepository.findAll();
    AuditLog log = allLogs.get(allLogs.size() - 1);

    // Verify audit log content
    assertNotNull(log.getId());
    assertEquals("GET", log.getMethod());
    assertEquals("/health", log.getPath());
    assertEquals(200, log.getStatus());
    assertEquals(testAccountId, log.getAccountId());
    assertTrue(log.getLatencyMs() >= 0);
  }

  /**
   * Integration test: AuditController queries AuditService which reads from repository.
   *
   * <p>Integrates: AuditController -> AuditService -> AuditLogRepository
   */
  @Test
  void testControllerToServiceToRepositoryIntegration() throws Exception {
    // Create test data
    mockMvc.perform(get("/health"));

    // Query through controller with authentication
    mockMvc.perform(get("/audit/logs")
                    // sec-investigator:sec-pass
                    .header("Authorization",
                            "Basic c2VjLWludmVzdGlnYXRvcjpzZWMtcGFzcw=="))
            .andExpect(status().isOk());
  }

  /**
   * Integration test: Security layer blocks unauthorized access to audit endpoints.
   *
   * <p>Integrates: SecurityConfig -> DatabaseUserDetailsService -> UserRepository
   */
  @Test
  void testSecurityIntegrationBlocksUnauthorizedAccess() throws Exception {
    mockMvc.perform(get("/audit/logs"))
        .andExpect(status().isUnauthorized());
  }

  /**
   * Integration test: Trader role blocked from audit logs.
   *
   * <p>Integrates: SecurityConfig -> DatabaseUserDetailsService -> Authorization Matrix
   */
  @Test
  void testSecurityIntegrationBlocksTraderRole() throws Exception {
    mockMvc.perform(get("/audit/logs")
          // trader-john:john-pass
          .header("Authorization",
            "Basic dHJhZGVyLWpvaG46am9obi1wYXNz"))
            .andExpect(status().isForbidden());
  }
}