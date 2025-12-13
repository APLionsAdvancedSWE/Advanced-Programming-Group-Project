package com.dev.tradingapi.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dev.tradingapi.dto.AccountCreateRequest;
import com.dev.tradingapi.dto.AccountCreateResponse;
import com.dev.tradingapi.dto.CreateOrderRequest;
import com.dev.tradingapi.model.Order;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * HTTP-level integration tests that start the full Spring Boot application
 * and exercise the exported REST APIs over real HTTP.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TradingApiHttpIntegrationTest {

  @LocalServerPort
  private int port;

  @Autowired
  private TestRestTemplate restTemplate;

  private String baseUrl(String path) {
    return "http://localhost:" + port + path;
  }

  @Test
  void createAccountAndSubmitOrder_overHttp_succeeds() {
    // 1) Create an account via the API
    AccountCreateRequest accountReq = new AccountCreateRequest();
    accountReq.setName("HTTP IT Account");
    accountReq.setUsername("http_it_user" + UUID.randomUUID());
    accountReq.setPassword("StrongP@ssword1");

    ResponseEntity<AccountCreateResponse> createAccountResp = restTemplate.postForEntity(
        baseUrl("/accounts/create"), accountReq, AccountCreateResponse.class);

    assertEquals(HttpStatus.CREATED, createAccountResp.getStatusCode(),
        "Unexpected status when creating account: " + createAccountResp);
    assertNotNull(createAccountResp.getBody(), "Account create body should not be null");
    UUID accountId = createAccountResp.getBody().getId();
    assertNotNull(accountId);
    assertNotNull(createAccountResp.getBody().getAuthToken());

    // 2) Submit an order for that account via the HTTP order endpoint
    CreateOrderRequest orderReq = new CreateOrderRequest();
    orderReq.setAccountId(accountId);
    orderReq.setClientOrderId("http-it-order-1");
    orderReq.setSymbol("IBM");
    orderReq.setSide("BUY");
    orderReq.setQty(10);
    orderReq.setType("MARKET");
    orderReq.setTimeInForce("DAY");

    ResponseEntity<Order> createOrderResp = restTemplate.postForEntity(
        baseUrl("/orders"), orderReq, Order.class);

    assertEquals(HttpStatus.CREATED, createOrderResp.getStatusCode(),
        "Unexpected status when creating order: " + createOrderResp);
    assertNotNull(createOrderResp.getBody());
    Order createdOrder = createOrderResp.getBody();
    assertEquals("IBM", createdOrder.getSymbol());
    // With the pure-book engine and IOC-style MARKET behavior,
    // the first MARKET BUY with no opposing orders is CANCELLED.
    assertEquals("CANCELLED", createdOrder.getStatus());

    // 3) Fetch the order back via GET /orders/{id}
    ResponseEntity<Order> getOrderResp = restTemplate.exchange(
        baseUrl("/orders/" + createdOrder.getId()),
        HttpMethod.GET,
        HttpEntity.EMPTY,
        Order.class);

    assertEquals(HttpStatus.OK, getOrderResp.getStatusCode());
    assertNotNull(getOrderResp.getBody());
    assertEquals(createdOrder.getId(), getOrderResp.getBody().getId());
  }

  @Test
  void createAccount_missingUsername_returnsBadRequest() {
    AccountCreateRequest badReq = new AccountCreateRequest();
    badReq.setName("Bad Name"); 
    badReq.setUsername(null); //no username
    badReq.setPassword("no-username");

    ResponseEntity<String> resp = restTemplate.postForEntity(
        baseUrl("/accounts/create"), badReq, String.class);

    // Expect 400 from controller validation
    assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode(),
              "Unexpected status for bad account request: " + resp);
    assertNotNull(resp.getBody());
  }
}
