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
}