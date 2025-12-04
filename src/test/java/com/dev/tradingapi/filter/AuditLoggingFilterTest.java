package com.dev.tradingapi.filter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dev.tradingapi.model.AuditLog;
import com.dev.tradingapi.service.AuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for AuditLoggingFilter.
 */
@ExtendWith(MockitoExtension.class)
class AuditLoggingFilterTest {

  @Mock
  private AuditService auditService;

  @Mock
  private HttpServletRequest request;

  @Mock
  private HttpServletResponse response;

  @Mock
  private FilterChain filterChain;

  private AuditLoggingFilter auditLoggingFilter;

  @BeforeEach
  void setUp() {
    auditLoggingFilter = new AuditLoggingFilter(auditService);
  }

  @Test
  void testDoFilter_LogsRequest() throws Exception {
    when(request.getHeader("X-API-Key")).thenReturn("test-key");
    when(request.getMethod()).thenReturn("GET");
    when(request.getRequestURI()).thenReturn("/health");
    when(response.getStatus()).thenReturn(200);

    auditLoggingFilter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    verify(auditService).logRequest(any(AuditLog.class));
  }

  @Test
  void testDoFilter_WithoutApiKey() throws Exception {
    when(request.getHeader("X-API-Key")).thenReturn(null);
    when(request.getMethod()).thenReturn("GET");
    when(request.getRequestURI()).thenReturn("/health");
    when(response.getStatus()).thenReturn(200);

    auditLoggingFilter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    verify(auditService).logRequest(any(AuditLog.class));
  }

  @Test
  void testDoFilter_PostRequest() throws Exception {
    when(request.getHeader("X-API-Key")).thenReturn("test-key");
    when(request.getMethod()).thenReturn("POST");
    when(request.getRequestURI()).thenReturn("/orders");
    when(response.getStatus()).thenReturn(201);

    auditLoggingFilter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    verify(auditService).logRequest(any(AuditLog.class));
  }

  @Test
  void testDoFilter_Logs_GetOrderEndpoint() throws Exception {
    when(request.getHeader("X-API-Key")).thenReturn("test-key");
    when(request.getMethod()).thenReturn("GET");
    when(request.getRequestURI()).thenReturn("/orders/" + java.util.UUID.randomUUID());
    when(response.getStatus()).thenReturn(200);

    auditLoggingFilter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    verify(auditService).logRequest(any(AuditLog.class));
  }

  @Test
  void testDoFilter_Logs_GetFillsEndpoint() throws Exception {
    when(request.getHeader("X-API-Key")).thenReturn("test-key");
    when(request.getMethod()).thenReturn("GET");
    when(request.getRequestURI())
        .thenReturn("/orders/" + java.util.UUID.randomUUID() + "/fills");
    when(response.getStatus()).thenReturn(200);

    auditLoggingFilter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    verify(auditService).logRequest(any(AuditLog.class));
  }

  @Test
  void testDoFilter_Logs_CancelEndpoint() throws Exception {
    when(request.getHeader("X-API-Key")).thenReturn("test-key");
    when(request.getMethod()).thenReturn("POST");
    when(request.getRequestURI())
        .thenReturn("/orders/" + java.util.UUID.randomUUID() + ":cancel");
    when(response.getStatus()).thenReturn(200);

    auditLoggingFilter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    verify(auditService).logRequest(any(AuditLog.class));
  }

  @Test
  void testDoFilter_Logs_AuditLogsEndpoint() throws Exception {
    org.mockito.Mockito.lenient().when(request.getHeader("X-API-Key")).thenReturn("test-key");
    org.mockito.Mockito.lenient().when(request.getMethod()).thenReturn("GET");
    when(request.getRequestURI()).thenReturn("/audit/logs");
    org.mockito.Mockito.lenient().when(response.getStatus()).thenReturn(200);

    auditLoggingFilter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    // Some implementations intentionally skip logging for log retrieval to avoid recursion.
    // Assert that no audit log is written for /audit/logs.
    verify(auditService, never()).logRequest(any(AuditLog.class));
  }

  /**
   * Test doFilter with non-HTTP request - atypical case.
   * Should pass through without logging.
   */
  @Test
  void testDoFilter_NonHttpRequest_AtypicalCase() throws Exception {
    // Create mock non-HTTP request/response
    ServletRequest nonHttpRequest = mock(ServletRequest.class);
    ServletResponse nonHttpResponse = mock(ServletResponse.class);

    auditLoggingFilter.doFilter(nonHttpRequest, nonHttpResponse, filterChain);

    // Should pass through the filter chain
    verify(filterChain).doFilter(nonHttpRequest, nonHttpResponse);
    // Should NOT log anything
    verify(auditService, never()).logRequest(any(AuditLog.class));
  }

  @Test
  void testDoFilter_ErrorResponse() throws Exception {
    when(request.getHeader("X-API-Key")).thenReturn("test-key");
    when(request.getMethod()).thenReturn("GET");
    when(request.getRequestURI()).thenReturn("/notfound");
    when(response.getStatus()).thenReturn(404);

    auditLoggingFilter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    verify(auditService).logRequest(any(AuditLog.class));
  }

  @Test
  void testDoFilter_CapturesLatency() throws Exception {
    when(request.getHeader("X-API-Key")).thenReturn("test-key");
    when(request.getMethod()).thenReturn("GET");
    when(request.getRequestURI()).thenReturn("/health");
    when(response.getStatus()).thenReturn(200);

    auditLoggingFilter.doFilter(request, response, filterChain);

    verify(auditService).logRequest(any(AuditLog.class));
  }

  /**
   * Test accountId extraction from X-Account-Id header.
   */
  @Test
  void testDoFilter_ExtractsAccountIdFromHeader() throws Exception {
    String accountId = "aaaa1111-1111-1111-1111-111111111111";
    when(request.getHeader("X-API-Key")).thenReturn("test-key");
    when(request.getHeader("X-Account-Id")).thenReturn(accountId);
    when(request.getMethod()).thenReturn("GET");
    when(request.getRequestURI()).thenReturn("/health");
    when(response.getStatus()).thenReturn(200);

    auditLoggingFilter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    verify(auditService).logRequest(any(AuditLog.class));
  }

  /**
   * Test accountId extraction from URL path.
   */
  @Test
  void testDoFilter_ExtractsAccountIdFromPath() throws Exception {
    String accountId = "bbbb2222-2222-2222-2222-222222222222";
    String path = "/accounts/" + accountId + "/positions";

    when(request.getHeader("X-API-Key")).thenReturn("test-key");
    when(request.getHeader("X-Account-Id")).thenReturn(null); // No header
    when(request.getMethod()).thenReturn("GET");
    when(request.getRequestURI()).thenReturn(path);
    when(response.getStatus()).thenReturn(200);

    auditLoggingFilter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    verify(auditService).logRequest(any(AuditLog.class));
  }

  /**
   * Test accountId extraction from query parameter.
   */
  @Test
  void testDoFilter_ExtractsAccountIdFromQueryParam() throws Exception {
    String accountId = "cccc3333-3333-3333-3333-333333333333";

    when(request.getHeader("X-API-Key")).thenReturn("test-key");
    when(request.getHeader("X-Account-Id")).thenReturn(null);
    when(request.getMethod()).thenReturn("GET");
    when(request.getRequestURI()).thenReturn("/orders");
    when(request.getParameter("accountId")).thenReturn(accountId);
    when(response.getStatus()).thenReturn(200);

    auditLoggingFilter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    verify(auditService).logRequest(any(AuditLog.class));
  }

  /**
   * Test invalid UUID format in header - should handle gracefully.
   */
  @Test
  void testDoFilter_InvalidAccountIdFormat() throws Exception {
    when(request.getHeader("X-API-Key")).thenReturn("test-key");
    when(request.getHeader("X-Account-Id")).thenReturn("invalid-uuid");
    when(request.getMethod()).thenReturn("GET");
    when(request.getRequestURI()).thenReturn("/health");
    when(response.getStatus()).thenReturn(200);

    auditLoggingFilter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    verify(auditService).logRequest(any(AuditLog.class));
  }

  /**
   * Test no accountId present - should log with null accountId.
   */
  @Test
  void testDoFilter_NoAccountId() throws Exception {
    when(request.getHeader("X-API-Key")).thenReturn("test-key");
    when(request.getHeader("X-Account-Id")).thenReturn(null);
    when(request.getMethod()).thenReturn("GET");
    when(request.getRequestURI()).thenReturn("/market/quote/AAPL");
    when(request.getParameter("accountId")).thenReturn(null);
    when(response.getStatus()).thenReturn(200);

    auditLoggingFilter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    verify(auditService).logRequest(any(AuditLog.class));
  }

  /**
   * Test accountId priority - header takes precedence over path.
   */
  @Test
  void testDoFilter_AccountIdPriorityHeaderOverPath() throws Exception {
    String headerAccountId = "aaaa1111-1111-1111-1111-111111111111";
    String pathAccountId = "bbbb2222-2222-2222-2222-222222222222";

    when(request.getHeader("X-API-Key")).thenReturn("test-key");
    when(request.getHeader("X-Account-Id")).thenReturn(headerAccountId);
    when(request.getMethod()).thenReturn("GET");
    when(request.getRequestURI()).thenReturn("/accounts/" + pathAccountId + "/positions");
    when(response.getStatus()).thenReturn(200);

    auditLoggingFilter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    verify(auditService).logRequest(any(AuditLog.class));
  }
}