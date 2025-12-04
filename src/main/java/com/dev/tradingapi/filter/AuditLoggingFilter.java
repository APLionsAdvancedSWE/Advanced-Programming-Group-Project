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
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Filter to automatically log all HTTP requests for audit purposes.
 * This filter intercepts every API call and records it to the database.
 */
@Component
public class AuditLoggingFilter implements Filter {
  private final AuditService auditService;

  // Pattern to extract UUID from paths like /accounts/{uuid}/...
  private static final Pattern ACCOUNT_PATH_PATTERN = Pattern.compile(
          "/accounts/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})"
  );

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

      // Extract request details
      String apiKey = httpRequest.getHeader("X-API-Key");
      String method = httpRequest.getMethod();
      int status = httpResponse.getStatus();

      // Extract and convert accountId from String to UUID
      String accountIdStr = extractAccountId(httpRequest, path);
      UUID accountId = null;
      if (accountIdStr != null) {
        try {
          accountId = UUID.fromString(accountIdStr);
        } catch (IllegalArgumentException e) {
          // Invalid UUID format - leave as null
          System.err.println("Invalid UUID format: " + e.getMessage());
        }
      }

      // Create and save audit log
      AuditLog log = new AuditLog(apiKey, accountId, method, path, status, (int) duration);
      auditService.logRequest(log);
    } else {
      // Not HTTP request/response, just continue
      chain.doFilter(request, response);
    }
  }

  /**
   * Extracts account ID from the request.
   * Tries multiple sources: header, path parameter, query parameter.
   *
   * @param request the HTTP request
   * @param path the request path
   * @return the account ID string if found, null otherwise
   */
  private String extractAccountId(HttpServletRequest request, String path) {
    // 1. Try X-Account-Id header
    String headerAccountId = request.getHeader("X-Account-Id");
    if (headerAccountId != null) {
      return headerAccountId;
    }

    // 2. Try extracting from path (e.g., /accounts/{uuid}/positions)
    try {
      Matcher matcher = ACCOUNT_PATH_PATTERN.matcher(path);
      if (matcher.find()) {
        return matcher.group(1);
      }
    } catch (Exception e) {
      // Regex pattern error - continue without accountId extraction
      System.err.println("Pattern matching error: " + e.getMessage());
    }

    // 3. Try query parameter
    try {
      String paramAccountId = request.getParameter("accountId");
      if (paramAccountId != null) {
        return paramAccountId;
      }
    } catch (Exception e) {
      // Parameter extraction error - continue without accountId extraction
      System.err.println("Parameter extraction error: " + e.getMessage());
    }

    // 4. No accountId found
    return null;
  }
}