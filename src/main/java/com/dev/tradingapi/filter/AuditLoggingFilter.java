package com.dev.tradingapi.filter;

import com.dev.tradingapi.model.AuditLog;
import com.dev.tradingapi.service.AuditService;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Filter to automatically log all HTTP requests for audit purposes.
 * This filter intercepts every API call and records it to the database.
 */
@Component
public class AuditLoggingFilter implements Filter {
  private final AuditService auditService;

  /**
   * Constructor with dependency injection.
   *
   * @param auditService the audit service for logging
   */
  @Autowired
  public AuditLoggingFilter(AuditService auditService) {
    this.auditService = auditService;
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response,
                       FilterChain chain) throws IOException, ServletException {
    if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
      HttpServletRequest httpRequest = (HttpServletRequest) request;
      HttpServletResponse httpResponse = (HttpServletResponse) response;
      
      // Extract request details early
      String path = httpRequest.getRequestURI();
      
      // Skip logging audit endpoints to avoid recursive logs
      if (path != null && path.startsWith("/audit")) {
        chain.doFilter(request, response);
        return;
      }
      
      // Record start time
      long startTime = System.currentTimeMillis();
      
      // Let the request proceed
      chain.doFilter(request, response);
      
      // Calculate duration
      long duration = System.currentTimeMillis() - startTime;
      
      // Extract other request details
      String apiKey = httpRequest.getHeader("X-API-Key");
      String method = httpRequest.getMethod();
      int status = httpResponse.getStatus();
      
      // Create and save audit log
      AuditLog log = new AuditLog(apiKey, method, path, status, (int) duration);
      auditService.logRequest(log);
    } else {
      // Not HTTP request/response, just continue
      chain.doFilter(request, response);
    }
  }
}
