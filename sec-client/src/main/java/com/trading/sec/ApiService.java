package com.trading.sec;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for making HTTP calls to the Trading API.
 * Handles authentication and response parsing for SEC investigations.
 */
public class ApiService {

  private final String baseUrl;
  private final String apiKey;
  private final Gson gson;
  private final CloseableHttpClient httpClient;

  public ApiService(String baseUrl, String apiKey) {
    this.baseUrl = baseUrl;
    this.apiKey = apiKey;
    this.gson = new Gson();
    this.httpClient = HttpClients.createDefault();
  }

  /**
   * Fetches audit logs from the API with optional filters.
   *
   * @param accountId filter by account (nullable)
   * @param from start timestamp ISO format (nullable)
   * @param to end timestamp ISO format (nullable)
   * @param page page number
   * @param pageSize results per page
   * @return list of audit log entries
   */
  public List<AuditLogEntry> fetchAuditLogs(String accountId, String from, String to,
                                            int page, int pageSize) {
    try {
      StringBuilder url = new StringBuilder(baseUrl + "/audit/logs?");
      url.append("page=").append(page);
      url.append("&pageSize=").append(pageSize);

      if (accountId != null) {
        url.append("&accountId=").append(accountId);
      }
      if (from != null) {
        url.append("&from=").append(from);
      }
      if (to != null) {
        url.append("&to=").append(to);
      }

      HttpGet request = new HttpGet(url.toString());
      request.setHeader("X-API-Key", apiKey);

      // Add Basic Authentication for SEC investigator
      String auth = "sec-investigator:sec-pass";
      String encodedAuth = java.util.Base64.getEncoder()
              .encodeToString(auth.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      request.setHeader("Authorization", "Basic " + encodedAuth);


      try (CloseableHttpResponse response = httpClient.execute(request)) {
        String jsonResponse = EntityUtils.toString(response.getEntity());
        JsonObject jsonObject = gson.fromJson(jsonResponse, JsonObject.class);

        List<AuditLogEntry> logs = new ArrayList<>();
        if (jsonObject.has("items")) {
          jsonObject.getAsJsonArray("items").forEach(item -> {
            logs.add(gson.fromJson(item, AuditLogEntry.class));
          });
        }

        return logs;
      }
    } catch (Exception e) {
      System.err.println("Error fetching audit logs: " + e.getMessage());
      return new ArrayList<>();
    }
  }

  /**
   * Checks if the service is healthy.
   *
   * @return true if service responds, false otherwise
   */
  public boolean checkHealth() {
    try {
      HttpGet request = new HttpGet(baseUrl + "/health");
      try (CloseableHttpResponse response = httpClient.execute(request)) {
        return response.getCode() == 200;
      }
    } catch (IOException e) {
      return false;
    }
  }

  /**
   * Closes the HTTP client.
   */
  public void close() {
    try {
      httpClient.close();
    } catch (IOException e) {
      System.err.println("Error closing HTTP client: " + e.getMessage());
    }
  }
}